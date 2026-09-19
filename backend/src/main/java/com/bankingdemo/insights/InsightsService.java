package com.bankingdemo.insights;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.budget.Budget;
import com.bankingdemo.budget.BudgetRepository;
import com.bankingdemo.insights.InsightsResponse.BudgetEstimate;
import com.bankingdemo.insights.InsightsResponse.CategoryAmount;
import com.bankingdemo.insights.InsightsResponse.GoalInsight;
import com.bankingdemo.insights.InsightsResponse.GoalProjection;
import com.bankingdemo.insights.InsightsResponse.Observed;
import com.bankingdemo.insights.InsightsResponse.Projection;
import com.bankingdemo.insights.forecast.GoalProjector;
import com.bankingdemo.insights.forecast.SpendingForecaster;
import com.bankingdemo.insights.forecast.SpendingForecaster.Payment;
import com.bankingdemo.insights.stats.SpendingStatistics;
import com.bankingdemo.ledger.GoalFlowFact;
import com.bankingdemo.ledger.LedgerDirection;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.ledger.RefundFact;
import com.bankingdemo.ledger.SpendingCategory;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import com.bankingdemo.ledger.SpendingFact;
import com.bankingdemo.savingsgoal.GoalStatus;
import com.bankingdemo.savingsgoal.SavingsGoal;
import com.bankingdemo.savingsgoal.SavingsGoalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assembles the Insights page for one customer. All numbers are computed here
 * and in the pure forecast/statistics classes -- never by a language model.
 * "Spending" is the same definition the budgets use: debits from the
 * customer's accounts except transfers between their own accounts (this also
 * removes savings-goal contributions). Deposits are credits and are never
 * counted as income or as a recurring salary.
 */
@Service
public class InsightsService {

    /** Upper bound on payments read per request (90 days of history); a truncated read is reported, not hidden. */
    private static final int MAX_PAYMENTS_READ = 5000;

    private static final List<String> METHODOLOGY = List.of(
            "Observed figures are what has already happened this UTC calendar month. Projections are estimates and are shown separately.",
            "Spending means outgoing payments from your accounts. Transfers between your own accounts (including savings-goal contributions) are excluded.",
            "Simulated deposits are never treated as income, salary or a recurring pattern.",
            "Refunds (credits from another customer labelled refund or reversal) reduce the amount they arrived on, never below zero for that day.",
            "The month-end projection needs at least 14 days of history, 10 payments, and payments on at least 25% of those days; otherwise it says \"Insufficient history\" instead of guessing.",
            "It applies your typical daily spending over your history (one-off spikes capped) to the days left this month. The range is the spread between two estimation methods, not a statistical confidence interval.",
            "No probabilities or confidence percentages are calculated or shown. Estimates are not guarantees.");

    private static final List<String> PROJECTION_ASSUMPTIONS = List.of(
            "Your spending for the rest of the month is similar to your typical day over the last 90 days.",
            "Rare payments far above your usual size (at most 10% of payments) are capped so a one-off purchase is not repeated every day; a regular pattern of large payments is not capped.",
            "Anything not yet paid is unknown, so unusually large upcoming bills are not anticipated.");

    private final LedgerEntryRepository ledgerEntryRepository;
    private final SpendingCategoryRepository spendingCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public InsightsService(LedgerEntryRepository ledgerEntryRepository,
                           SpendingCategoryRepository spendingCategoryRepository,
                           BudgetRepository budgetRepository,
                           SavingsGoalRepository savingsGoalRepository,
                           AccountRepository accountRepository,
                           Clock clock) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.spendingCategoryRepository = spendingCategoryRepository;
        this.budgetRepository = budgetRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsResponse compute(Long customerId) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant lookbackStart = today.minusDays(SpendingForecaster.LOOKBACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<Long, String> categoryNames = spendingCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(SpendingCategory::getId, SpendingCategory::getName));

        List<SpendingFact> facts = ledgerEntryRepository.spendingBetween(customerId, lookbackStart, now, null,
                PageRequest.of(0, MAX_PAYMENTS_READ));
        List<Payment> payments = facts.stream()
                .map(f -> new Payment(f.createdAt(), f.amount(), f.categoryId())).toList();
        List<Payment> refunds = ledgerEntryRepository.refundsBetween(customerId, lookbackStart, now).stream()
                .map(r -> new Payment(r.createdAt(), r.amount(), null)).toList();

        Observed observed = observed(payments, refunds, categoryNames, monthStart, monthStartInstant, today);
        Projection projection = projection(payments, refunds, now, facts.size() >= MAX_PAYMENTS_READ);
        List<BudgetEstimate> budgets = budgets(customerId, monthStart, payments, now, categoryNames);
        List<GoalInsight> goals = goals(customerId, now);

        return new InsightsResponse(now.toString(), today.toString(), "INR", observed, projection, budgets, goals, METHODOLOGY);
    }

    private Observed observed(List<Payment> payments, List<Payment> refunds, Map<Long, String> categoryNames,
                              LocalDate monthStart, Instant monthStartInstant, LocalDate today) {
        List<Payment> thisMonth = payments.stream().filter(p -> !p.at().isBefore(monthStartInstant)).toList();
        BigDecimal gross = SpendingStatistics.sum(thisMonth.stream().map(Payment::amount).toList());
        BigDecimal refunded = SpendingStatistics.sum(refunds.stream()
                .filter(r -> !r.at().isBefore(monthStartInstant)).map(Payment::amount).toList());
        BigDecimal netted = SpendingStatistics.min(refunded, gross);

        Map<Long, BigDecimal> byCategory = new LinkedHashMap<>();
        BigDecimal uncategorized = BigDecimal.ZERO;
        for (Payment p : thisMonth) {
            if (p.categoryId() == null) {
                uncategorized = uncategorized.add(p.amount());
            } else {
                byCategory.merge(p.categoryId(), p.amount(), BigDecimal::add);
            }
        }
        List<CategoryAmount> categories = byCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryAmount(e.getKey(), categoryNames.getOrDefault(e.getKey(), "Category"), money(e.getValue())))
                .toList();

        return new Observed(monthStart.toString(), today.getDayOfMonth(), today.lengthOfMonth(), thisMonth.size(),
                money(gross), money(netted), money(gross.subtract(netted)), categories, money(uncategorized));
    }

    private Projection projection(List<Payment> payments, List<Payment> refunds, Instant now, boolean truncated) {
        SpendingForecaster.Result r = SpendingForecaster.project(payments, p -> true, refunds, now,
                SpendingForecaster.MIN_PAYMENTS, SpendingForecaster.MIN_ACTIVE_DAY_SHARE_TOTAL);
        if (r.status() != SpendingForecaster.Status.OK) {
            return new Projection("INSUFFICIENT_HISTORY", r.reason(), null, null, null, null, List.of());
        }
        SpendingForecaster.Basis b = r.basis();
        List<String> assumptions = new ArrayList<>(PROJECTION_ASSUMPTIONS);
        if (truncated) {
            assumptions.add("Only the most recent " + MAX_PAYMENTS_READ + " payments were read, so older history is not reflected.");
        }
        return new Projection("OK", null, money(r.projected()), money(r.low()), money(r.high()),
                new InsightsResponse.Basis(b.windowStart().toString(), b.windowDays(), b.paymentsInWindow(),
                        money(b.dailyRate()), b.weeklyMedianDailyRate() == null ? null : money(b.weeklyMedianDailyRate()),
                        b.outlierCap() == null ? null : money(b.outlierCap()), b.capApplied(), b.remainingDays().toPlainString()),
                assumptions);
    }

    private List<BudgetEstimate> budgets(Long customerId, LocalDate monthStart, List<Payment> payments, Instant now,
                                         Map<Long, String> categoryNames) {
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        List<BudgetEstimate> result = new ArrayList<>();
        for (Budget budget : budgetRepository.findByCustomerIdAndMonthStart(customerId, monthStart)) {
            Long categoryId = budget.getCategoryId();
            String name = categoryNames.getOrDefault(categoryId, "Category");
            BigDecimal spent = SpendingStatistics.sum(payments.stream()
                    .filter(p -> categoryId.equals(p.categoryId()) && !p.at().isBefore(monthStartInstant))
                    .map(Payment::amount).toList());
            BigDecimal limit = budget.getLimitAmount();

            if (spent.compareTo(limit) >= 0) {
                result.add(new BudgetEstimate(categoryId, name, money(limit), money(spent), "ALREADY_OVER", null,
                        money(spent.subtract(limit)), "Spending in this category has already reached the limit."));
                continue;
            }
            SpendingForecaster.Result r = SpendingForecaster.project(payments, p -> categoryId.equals(p.categoryId()),
                    List.of(), now, SpendingForecaster.MIN_PAYMENTS_PER_CATEGORY, SpendingForecaster.MIN_ACTIVE_DAY_SHARE_CATEGORY);
            if (r.status() != SpendingForecaster.Status.OK) {
                result.add(new BudgetEstimate(categoryId, name, money(limit), money(spent), "INSUFFICIENT_HISTORY",
                        null, null, r.reason()));
            } else if (r.projected().compareTo(limit) > 0) {
                result.add(new BudgetEstimate(categoryId, name, money(limit), money(spent), "PROJECTED_OVER",
                        money(r.projected()), money(r.projected().subtract(limit)),
                        "At your typical pace this category is projected to pass its limit by month end."));
            } else if (r.high().compareTo(limit) > 0) {
                result.add(new BudgetEstimate(categoryId, name, money(limit), money(spent), "POSSIBLY_OVER",
                        money(r.projected()), null,
                        "The main estimate stays within the limit, but the higher estimate would pass it."));
            } else {
                result.add(new BudgetEstimate(categoryId, name, money(limit), money(spent), "ON_TRACK",
                        money(r.projected()), null, "Both estimates stay within the limit."));
            }
        }
        return result;
    }

    private List<GoalInsight> goals(Long customerId, Instant now) {
        List<GoalInsight> result = new ArrayList<>();
        for (SavingsGoal goal : savingsGoalRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)) {
            if (goal.getStatus() == GoalStatus.CLOSED) {
                continue;
            }
            BigDecimal saved = accountRepository.findById(goal.getLinkedAccountId()).map(Account::getBalance).orElse(BigDecimal.ZERO);
            BigDecimal target = goal.getTargetAmount();
            BigDecimal remaining = SpendingStatistics.max(BigDecimal.ZERO, target.subtract(saved));
            BigDecimal percent = SpendingStatistics.min(new BigDecimal("100"),
                    saved.multiply(new BigDecimal("100")).divide(target, 1, RoundingMode.DOWN));

            List<GoalProjector.Flow> flows = new ArrayList<>();
            for (GoalFlowFact f : ledgerEntryRepository.transferFlowsForAccount(goal.getLinkedAccountId(), goal.getCreatedAt(), now)) {
                flows.add(new GoalProjector.Flow(f.createdAt(), f.direction() == LedgerDirection.CREDIT ? f.amount() : f.amount().negate()));
            }
            GoalProjector.Result r = GoalProjector.project(target, saved, goal.getCreatedAt(), goal.getTargetDate(), flows, now);

            List<String> assumptions = r.status() == GoalProjector.Status.OK
                    ? List.of("Contributions continue at the average of your last " + r.monthsUsed() + " complete months.",
                    "Only transfers into and out of this goal count; simulated deposits are not treated as contributions.",
                    "This is an estimate, not a guaranteed date.")
                    : List.of();
            GoalProjection projection = new GoalProjection(r.status().name(), r.reason(),
                    r.averageMonthlyContribution() == null ? null : money(r.averageMonthlyContribution()),
                    r.monthsUsed(), r.estimatedCompletionMonth() == null ? null : r.estimatedCompletionMonth().toString(),
                    r.onPaceForTargetDate(), assumptions);
            result.add(new GoalInsight(goal.getId(), goal.getName(), goal.getStatus().name(), money(target), money(saved),
                    money(remaining), percent.toPlainString(),
                    goal.getTargetDate() == null ? null : goal.getTargetDate().toString(),
                    r.requiredMonthlyForTargetDate() == null ? null : money(r.requiredMonthlyForTargetDate()), projection));
        }
        return result;
    }

    private static String money(BigDecimal value) {
        return SpendingStatistics.money(value).toPlainString();
    }
}
