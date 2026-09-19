package com.bankingdemo.ai.assistant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a model-written answer against the backend-computed context it was
 * given. The model is never trusted with facts: every number and date it
 * writes must literally correspond to something in the context. That makes
 * invented amounts, wrong totals (including model-computed sums), and made-up
 * dates or percentages fail verification, after which the caller discards the
 * model text and uses the deterministic answer instead.
 *
 * What it can and cannot catch: it proves each stated number/date/reference
 * exists in the customer's own data. It does not prove the sentence pairs
 * them correctly (e.g. swapping two real amounts), which is why the UI also
 * renders the underlying figures and transactions straight from backend
 * fields next to the narrative.
 */
final class AssistantAnswerValidator {

    enum Rejection {
        EMPTY, TOO_LONG, FORBIDDEN_CONTENT, UNKNOWN_REFERENCE, UNVERIFIED_NUMBER
    }

    private static final int MAX_ANSWER_CHARS = 700;
    private static final Pattern REFERENCE = Pattern.compile("TXN-[0-9A-Za-z-]{6,}");
    private static final Pattern NUMBER = Pattern.compile("\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|\\d+(?:\\.\\d+)?");
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)https?:|www\\.|```|<[a-z/][^>]*>|\\]\\(");

    private AssistantAnswerValidator() {
    }

    static Optional<Rejection> validate(String answer, AssistantContext context) {
        if (answer == null || answer.isBlank()) {
            return Optional.of(Rejection.EMPTY);
        }
        if (answer.length() > MAX_ANSWER_CHARS) {
            return Optional.of(Rejection.TOO_LONG);
        }
        if (FORBIDDEN.matcher(answer).find()) {
            return Optional.of(Rejection.FORBIDDEN_CONTENT);
        }

        Set<String> knownReferences = new HashSet<>();
        context.largestPaymentsLast90Days().forEach(f -> knownReferences.add(f.reference()));
        context.recentTransactions().forEach(f -> knownReferences.add(f.reference()));

        Matcher refs = REFERENCE.matcher(answer);
        while (refs.find()) {
            if (!knownReferences.contains(refs.group())) {
                return Optional.of(Rejection.UNKNOWN_REFERENCE);
            }
        }
        // References contain digits; remove them so their digits are not mistaken for claimed numbers.
        String withoutReferences = REFERENCE.matcher(answer).replaceAll(" ");

        List<BigDecimal> allowed = allowedNumbers(context);
        Matcher numbers = NUMBER.matcher(withoutReferences);
        while (numbers.find()) {
            BigDecimal value = new BigDecimal(numbers.group().replace(",", ""));
            if (allowed.stream().noneMatch(a -> a.compareTo(value) == 0)) {
                return Optional.of(Rejection.UNVERIFIED_NUMBER);
            }
        }
        return Optional.empty();
    }

    private static List<BigDecimal> allowedNumbers(AssistantContext ctx) {
        List<BigDecimal> allowed = new java.util.ArrayList<>();
        addMoney(allowed, ctx.totalSpendThisMonth());
        addMoney(allowed, ctx.totalSpendLastMonth());
        ctx.accounts().forEach(a -> addMoney(allowed, a.balance()));
        ctx.spendByCategoryThisMonth().forEach(c -> addMoney(allowed, c.amount()));
        ctx.largestPaymentsLast90Days().forEach(t -> addMoney(allowed, t.amount()));
        ctx.recentTransactions().forEach(t -> addMoney(allowed, t.amount()));

        // Counts of things the model was actually shown.
        for (int size : new int[]{ctx.accounts().size(), ctx.spendByCategoryThisMonth().size(),
                ctx.largestPaymentsLast90Days().size(), ctx.recentTransactions().size()}) {
            allowed.add(BigDecimal.valueOf(size));
        }

        // Date components (year, month, day) of every date in the context.
        List<String> dates = new java.util.ArrayList<>();
        dates.add(ctx.todayUtc());
        ctx.largestPaymentsLast90Days().forEach(t -> dates.add(t.date()));
        ctx.recentTransactions().forEach(t -> dates.add(t.date()));
        for (String date : dates) {
            LocalDate d = parseDate(date);
            if (d != null) {
                allowed.add(BigDecimal.valueOf(d.getYear()));
                allowed.add(BigDecimal.valueOf(d.getMonthValue()));
                allowed.add(BigDecimal.valueOf(d.getDayOfMonth()));
            }
        }
        LocalDate today = parseDate(ctx.todayUtc());
        if (today != null) {
            LocalDate lastMonth = today.minusMonths(1);
            allowed.add(BigDecimal.valueOf(lastMonth.getYear()));
            allowed.add(BigDecimal.valueOf(lastMonth.getMonthValue()));
        }
        return allowed;
    }

    private static void addMoney(List<BigDecimal> allowed, String amount) {
        if (amount != null && !amount.isBlank()) {
            allowed.add(new BigDecimal(amount));
        }
    }

    private static LocalDate parseDate(String isoDateOrInstant) {
        if (isoDateOrInstant == null || isoDateOrInstant.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(isoDateOrInstant.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
