package com.bankingdemo.ai.assistant;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.TransactionHistoryRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, non-AI answers for a small set of recognized questions. This
 * is what keeps the assistant working when Groq is unconfigured, rate-limited,
 * or down -- every number here is computed the same way the rest of the
 * banking backend computes it (same repositories, same BigDecimal math), not
 * guessed. Also used as the AI-enriched path's grounding truth for these two
 * question types.
 */
@Component
public class RuleBasedAssistant {

    private static final int LARGEST_PAYMENTS_LIMIT = 5;
    private static final int LOOKBACK_DAYS = 90;
    private static final String NO_MATCH_ANSWER =
            "I can directly calculate your total spending this month or list your largest payments. "
                    + "Try asking \"How much did I spend this month?\" or \"Show my largest payments.\" "
                    + "Free-form AI answers are currently unavailable.";

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public RuleBasedAssistant(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public AssistantAskResponse answer(Long customerId, String question) {
        String q = question.toLowerCase(Locale.ROOT);

        if (containsAny(q, "largest", "biggest", "highest") && containsAny(q, "payment", "transaction", "expense", "spend", "purchase")) {
            return largestPayments(customerId);
        }
        if (containsAny(q, "balance")) {
            return balances(customerId);
        }
        if (containsAny(q, "spend", "spent", "spending")) {
            return spendingThisMonth(customerId);
        }
        return AssistantAskResponse.deterministic(NO_MATCH_ANSWER, List.of());
    }

    private AssistantAskResponse spendingThisMonth(Long customerId) {
        LocalDate monthStart = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        var from = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = monthStart.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal total = ledgerEntryRepository.sumAllSpendingForCustomer(customerId, from, to);
        return AssistantAskResponse.deterministic(
                "You spent ₹" + total.toPlainString() + " so far this month.", List.of());
    }

    private AssistantAskResponse largestPayments(Long customerId) {
        var since = LocalDate.now(ZoneOffset.UTC).minusDays(LOOKBACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<TransactionHistoryRow> rows = ledgerEntryRepository.largestSpendingForCustomer(
                customerId, since, PageRequest.of(0, LARGEST_PAYMENTS_LIMIT));
        if (rows.isEmpty()) {
            return AssistantAskResponse.deterministic("You have no outgoing payments in the last " + LOOKBACK_DAYS + " days.", List.of());
        }
        StringBuilder sb = new StringBuilder("Your largest payments in the last " + LOOKBACK_DAYS + " days:");
        List<String> refs = rows.stream().map(TransactionHistoryRow::reference).toList();
        for (TransactionHistoryRow row : rows) {
            sb.append(String.format(" ₹%s (%s, ref %s);", row.amount().toPlainString(),
                    row.description() == null || row.description().isBlank() ? "no description" : row.description(),
                    row.reference()));
        }
        return AssistantAskResponse.deterministic(sb.toString(), refs);
    }

    private AssistantAskResponse balances(Long customerId) {
        List<Account> accounts = accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(customerId);
        if (accounts.isEmpty()) {
            return AssistantAskResponse.deterministic("You don't have any accounts yet.", List.of());
        }
        StringBuilder sb = new StringBuilder("Your account balances:");
        for (Account account : accounts) {
            String label = account.getNickname() != null && !account.getNickname().isBlank()
                    ? account.getNickname() : account.getAccountType().name();
            sb.append(String.format(" %s: ₹%s;", label, account.getBalance().toPlainString()));
        }
        return AssistantAskResponse.deterministic(sb.toString(), List.of());
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
