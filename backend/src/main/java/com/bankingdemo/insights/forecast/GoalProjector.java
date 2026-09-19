package com.bankingdemo.insights.forecast;

import com.bankingdemo.insights.stats.SpendingStatistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Savings-goal progress and (only when the history supports it) a completion
 * estimate. Pure and clock-free, like {@link SpendingForecaster}.
 *
 * Progress (saved / remaining / percent) is observed fact: the real balance of
 * the goal's dedicated account. The estimate is a projection and is only made
 * when:
 *  - at least 2 complete calendar months exist after the month the goal was
 *    created (the creation month and the current month are partial, so they
 *    are excluded), and
 *  - the average NET monthly contribution over the last (up to) 3 of those
 *    months is positive.
 * Contributions are TRANSFER movements into the goal account minus TRANSFER
 * movements out. Simulated deposits/withdrawals directly on the goal account
 * are excluded on purpose: funding is not a recurring contribution habit.
 *
 * The estimate is "month N if contributions continue at that average" -- never
 * a guaranteed date and never accompanied by a confidence percentage.
 */
public final class GoalProjector {

    public static final int MIN_COMPLETE_MONTHS = 2;
    public static final int MAX_MONTHS_AVERAGED = 3;
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30.4375");

    private GoalProjector() {
    }

    public record Flow(Instant at, BigDecimal signedAmount) {
    }

    public enum Status {
        OK, COMPLETED, INSUFFICIENT_HISTORY, NO_POSITIVE_TREND
    }

    public record Result(Status status, String reason, BigDecimal averageMonthlyContribution, int monthsUsed,
                         YearMonth estimatedCompletionMonth, BigDecimal requiredMonthlyForTargetDate,
                         Boolean onPaceForTargetDate) {
    }

    public static Result project(BigDecimal target, BigDecimal saved, Instant goalCreatedAt, LocalDate targetDate,
                                 List<Flow> flows, Instant asOf) {
        LocalDate today = asOf.atZone(ZoneOffset.UTC).toLocalDate();
        BigDecimal remaining = SpendingStatistics.max(BigDecimal.ZERO, target.subtract(saved));

        if (remaining.signum() == 0) {
            return new Result(Status.COMPLETED, "Target reached.", null, 0, null, null, null);
        }

        BigDecimal required = null;
        if (targetDate != null && targetDate.isAfter(today)) {
            BigDecimal monthsLeft = SpendingStatistics.divide(
                    BigDecimal.valueOf(ChronoUnit.DAYS.between(today, targetDate)), DAYS_PER_MONTH);
            required = SpendingStatistics.money(remaining.divide(monthsLeft.max(new BigDecimal("0.01")), 10, RoundingMode.HALF_UP));
        }

        YearMonth firstCompleteMonth = YearMonth.from(goalCreatedAt.atZone(ZoneOffset.UTC)).plusMonths(1);
        YearMonth lastCompleteMonth = YearMonth.from(today).minusMonths(1);
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = lastCompleteMonth; !m.isBefore(firstCompleteMonth) && months.size() < MAX_MONTHS_AVERAGED; m = m.minusMonths(1)) {
            months.add(m);
        }
        if (months.size() < MIN_COMPLETE_MONTHS) {
            return new Result(Status.INSUFFICIENT_HISTORY,
                    "At least " + MIN_COMPLETE_MONTHS + " complete calendar months of contributions are needed "
                            + "before a completion estimate is shown.", null, months.size(), null, required, null);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (YearMonth month : months) {
            for (Flow flow : flows) {
                if (flow.at().isBefore(asOf) && YearMonth.from(flow.at().atZone(ZoneOffset.UTC)).equals(month)) {
                    total = total.add(flow.signedAmount());
                }
            }
        }
        BigDecimal average = total.divide(BigDecimal.valueOf(months.size()), 10, RoundingMode.HALF_UP);
        if (average.signum() <= 0) {
            return new Result(Status.NO_POSITIVE_TREND,
                    "Net contributions over the last " + months.size() + " complete months were not positive, "
                            + "so no completion date is estimated.", SpendingStatistics.money(average), months.size(), null, required, null);
        }

        int monthsToGo = remaining.divide(average, 0, RoundingMode.CEILING).intValueExact();
        YearMonth estimate = YearMonth.from(today).plusMonths(monthsToGo);
        Boolean onPace = required == null ? null : average.compareTo(required) >= 0;
        return new Result(Status.OK, null, SpendingStatistics.money(average), months.size(), estimate, required, onPace);
    }
}
