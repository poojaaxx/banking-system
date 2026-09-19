package com.bankingdemo.insights;

import java.util.List;

/**
 * Everything on the Insights page. Observed figures (what already happened)
 * are kept in separate objects from projections (estimates), and every
 * projection carries its status, reason and assumptions. There are no
 * confidence percentages anywhere: none are computed, so none are shown.
 * Amounts are decimal strings in INR.
 */
public record InsightsResponse(
        String generatedAt,
        String asOfDate,
        String currency,
        Observed observed,
        Projection projection,
        List<BudgetEstimate> budgets,
        List<GoalInsight> goals,
        List<String> methodology
) {

    /** Facts about the current UTC calendar month so far. */
    public record Observed(String monthStart, int daysElapsed, int daysInMonth, int paymentCount,
                           String grossSpent, String refundsNetted, String netSpent,
                           List<CategoryAmount> byCategory, String uncategorized) {
    }

    public record CategoryAmount(Long categoryId, String name, String amount) {
    }

    /** {@code status} is OK or INSUFFICIENT_HISTORY. Numbers are null unless OK. */
    public record Projection(String status, String reason, String projectedMonthEnd, String rangeLow,
                             String rangeHigh, Basis basis, List<String> assumptions) {
    }

    public record Basis(String windowStart, int windowDays, int paymentsInWindow, String dailyRate,
                        String weeklyMedianDailyRate, String largestPaymentCap, boolean cappedOneOffs,
                        String remainingDaysInMonth) {
    }

    /** status: ALREADY_OVER, PROJECTED_OVER, POSSIBLY_OVER, ON_TRACK or INSUFFICIENT_HISTORY. */
    public record BudgetEstimate(Long categoryId, String category, String limit, String spent, String status,
                                 String projectedMonthEnd, String estimatedOverrun, String reason) {
    }

    public record GoalInsight(Long goalId, String name, String goalStatus, String target, String saved,
                              String remaining, String percentComplete, String targetDate,
                              String requiredMonthlyForTargetDate, GoalProjection projection) {
    }

    /** status: OK, COMPLETED, INSUFFICIENT_HISTORY or NO_POSITIVE_TREND. */
    public record GoalProjection(String status, String reason, String averageMonthlyContribution,
                                 int completeMonthsUsed, String estimatedCompletionMonth,
                                 Boolean onPaceForTargetDate, List<String> assumptions) {
    }
}
