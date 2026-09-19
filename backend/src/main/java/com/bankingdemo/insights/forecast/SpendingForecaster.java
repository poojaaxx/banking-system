package com.bankingdemo.insights.forecast;

import com.bankingdemo.insights.stats.SpendingStatistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Projects month-end spending from a customer's own payment history.
 *
 * Pure: no database, no clock, no randomness. The caller supplies the payments
 * and the "as of" instant; anything at or after that instant is ignored, so a
 * forecast can never use future data (this is what lets the held-out
 * evaluation in the tests be honest).
 *
 * Method (documented in docs/insights.md):
 *  1. History window = every complete UTC day from the customer's first
 *     payment inside the last 90 days up to yesterday. Days with no spending
 *     count as zero-spend days. Needs >= 14 days, >= 10 payments, and payments
 *     on at least 25% of those days (10% for a single category); otherwise the
 *     result is INSUFFICIENT_HISTORY -- nothing is projected. The last gate
 *     exists because held-out testing showed lumpy, infrequent spending cannot
 *     be extrapolated as a daily rate (errors of 60-80%).
 *  2. One-off spikes are damped: when there are >= 8 payments, each payment is
 *     capped at the Tukey far-out fence (Q3 + 3*IQR) of the window's payments --
 *     but only if at most 10% of payments exceed that fence. If more do, the
 *     large payments are a regular pattern, not one-offs, and nothing is capped.
 *  3. Refunds (credits from another customer labelled refund/reversal) reduce
 *     the day they arrived, never below zero for that day.
 *  4. Estimate A = capped, refund-netted spend per day over the window.
 *     Estimate B = median weekly total / 7 (needs >= 2 whole weeks).
 *  5. Projected month-end = spent so far this month (net of refunds) +
 *     estimate A x remaining time in the month (whole days after today plus
 *     the unelapsed fraction of today).
 *     The range runs between the A-based and B-based projections. It is the
 *     spread between two estimation methods -- NOT a statistical confidence
 *     interval, and no probability is attached to it.
 */
public final class SpendingForecaster {

    public static final int LOOKBACK_DAYS = 90;
    public static final int MIN_HISTORY_DAYS = 14;
    public static final int MIN_PAYMENTS = 10;
    public static final int MIN_PAYMENTS_PER_CATEGORY = 3;
    /** Share of history days that must contain a payment before a daily-rate projection is meaningful. */
    public static final double MIN_ACTIVE_DAY_SHARE_TOTAL = 0.25;
    public static final double MIN_ACTIVE_DAY_SHARE_CATEGORY = 0.10;
    public static final int OUTLIER_CAP_MIN_PAYMENTS = 8;
    public static final double OUTLIER_CAP_K = 3.0;
    /** Payments above the fence are only treated as one-off spikes (and capped) when they are at most this share of payments. */
    public static final double MAX_ONE_OFF_SHARE = 0.10;

    private static final long SECONDS_PER_DAY = 86_400L;

    private SpendingForecaster() {
    }

    public record Payment(Instant at, BigDecimal amount, Long categoryId) {
    }

    public enum Status {
        OK, INSUFFICIENT_HISTORY
    }

    public record Basis(LocalDate windowStart, int windowDays, int paymentsInWindow, BigDecimal dailyRate,
                        BigDecimal weeklyMedianDailyRate, BigDecimal outlierCap, boolean capApplied,
                        BigDecimal remainingDays) {
    }

    public record Result(Status status, String reason, BigDecimal spentToDate, BigDecimal projected,
                         BigDecimal low, BigDecimal high, Basis basis) {

        static Result insufficient(String reason, BigDecimal spentToDate) {
            return new Result(Status.INSUFFICIENT_HISTORY, reason, spentToDate, null, null, null, null);
        }
    }

    /**
     * @param allPayments every payment by the customer (defines the history window; only entries before asOf are used)
     * @param include     which of those payments the projection is about (all of them for the total, one category for a budget)
     * @param refunds     refund credits to net against the projection's daily series (empty for category projections)
     * @param minPayments minimum included payments in the window before a projection is shown
     * @param minActiveDayShare minimum share of window days that must contain an included payment
     */
    public static Result project(List<Payment> allPayments, Predicate<Payment> include, List<Payment> refunds,
                                 Instant asOf, int minPayments, double minActiveDayShare) {
        LocalDate today = asOf.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate monthStart = today.withDayOfMonth(1);
        Instant startOfToday = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStartInstant = monthStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant lookbackStart = today.minusDays(LOOKBACK_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Payment> known = allPayments.stream().filter(p -> p.at().isBefore(asOf)).toList();
        List<Payment> knownRefunds = refunds.stream().filter(r -> r.at().isBefore(asOf)).toList();

        BigDecimal gross = SpendingStatistics.sum(known.stream()
                .filter(include).filter(p -> !p.at().isBefore(monthStartInstant)).map(Payment::amount).toList());
        BigDecimal refundedThisMonth = SpendingStatistics.sum(knownRefunds.stream()
                .filter(r -> !r.at().isBefore(monthStartInstant)).map(Payment::amount).toList());
        BigDecimal spentToDate = SpendingStatistics.max(BigDecimal.ZERO, gross.subtract(refundedThisMonth));

        // Window: complete days only (today is still in progress), starting at the first payment in the lookback.
        List<Payment> windowAll = known.stream()
                .filter(p -> !p.at().isBefore(lookbackStart) && p.at().isBefore(startOfToday)).toList();
        if (windowAll.isEmpty()) {
            return Result.insufficient("No completed spending days in the last " + LOOKBACK_DAYS + " days yet.", spentToDate);
        }
        LocalDate windowStart = windowAll.stream()
                .map(p -> p.at().atZone(ZoneOffset.UTC).toLocalDate()).min(LocalDate::compareTo).orElseThrow();
        int windowDays = (int) ChronoUnit.DAYS.between(windowStart, today);
        if (windowDays < MIN_HISTORY_DAYS) {
            return Result.insufficient("Only " + windowDays + " day(s) of history so far; at least "
                    + MIN_HISTORY_DAYS + " are needed.", spentToDate);
        }
        List<Payment> windowIncluded = windowAll.stream().filter(include).toList();
        if (windowIncluded.size() < minPayments) {
            return Result.insufficient("Only " + windowIncluded.size() + " payment(s) in the window; at least "
                    + minPayments + " are needed.", spentToDate);
        }
        long activeDays = windowIncluded.stream()
                .map(p -> p.at().atZone(ZoneOffset.UTC).toLocalDate()).distinct().count();
        if (activeDays < Math.ceil(windowDays * minActiveDayShare)) {
            return Result.insufficient("Spending is too infrequent or irregular to project: payments on " + activeDays
                    + " of " + windowDays + " days (at least " + Math.round(minActiveDayShare * 100) + "% of days are needed).", spentToDate);
        }

        // Damp one-off spikes so a single large purchase is not extrapolated across the rest of the month.
        BigDecimal cap = null;
        boolean capApplied = false;
        if (windowIncluded.size() >= OUTLIER_CAP_MIN_PAYMENTS) {
            BigDecimal fence = SpendingStatistics.tukeyUpperFence(windowIncluded.stream().map(Payment::amount).toList(), OUTLIER_CAP_K);
            long beyondFence = windowIncluded.stream().filter(p -> p.amount().compareTo(fence) > 0).count();
            if (beyondFence <= Math.floor(windowIncluded.size() * MAX_ONE_OFF_SHARE)) {
                cap = fence;
            }
        }
        BigDecimal[] daily = new BigDecimal[windowDays];
        java.util.Arrays.fill(daily, BigDecimal.ZERO);
        for (Payment p : windowIncluded) {
            BigDecimal amount = p.amount();
            if (cap != null && amount.compareTo(cap) > 0) {
                amount = cap;
                capApplied = true;
            }
            int index = (int) ChronoUnit.DAYS.between(windowStart, p.at().atZone(ZoneOffset.UTC).toLocalDate());
            daily[index] = daily[index].add(amount);
        }
        for (Payment r : knownRefunds) {
            if (r.at().isBefore(startOfToday) && !r.at().isBefore(windowStart.atStartOfDay(ZoneOffset.UTC).toInstant())) {
                int index = (int) ChronoUnit.DAYS.between(windowStart, r.at().atZone(ZoneOffset.UTC).toLocalDate());
                daily[index] = SpendingStatistics.max(BigDecimal.ZERO, daily[index].subtract(r.amount()));
            }
        }

        BigDecimal daysInWindow = BigDecimal.valueOf(windowDays);
        BigDecimal rateA = SpendingStatistics.divide(SpendingStatistics.sum(List.of(daily)), daysInWindow);

        BigDecimal rateB = null;
        int wholeWeeks = windowDays / 7;
        if (wholeWeeks >= 2) {
            List<BigDecimal> weekly = new ArrayList<>();
            for (int w = 0; w < wholeWeeks; w++) {
                BigDecimal weekTotal = BigDecimal.ZERO;
                for (int d = 0; d < 7; d++) {
                    weekTotal = weekTotal.add(daily[w * 7 + d]);
                }
                weekly.add(weekTotal);
            }
            rateB = SpendingStatistics.divide(SpendingStatistics.median(weekly), BigDecimal.valueOf(7));
        }

        long secondOfDay = asOf.atZone(ZoneOffset.UTC).toLocalTime().toSecondOfDay();
        BigDecimal fractionLeftToday = BigDecimal.valueOf(SECONDS_PER_DAY - secondOfDay)
                .divide(BigDecimal.valueOf(SECONDS_PER_DAY), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal remainingDays = BigDecimal.valueOf(today.lengthOfMonth() - today.getDayOfMonth()).add(fractionLeftToday);

        BigDecimal lowRate = rateB == null ? rateA : SpendingStatistics.min(rateA, rateB);
        BigDecimal highRate = rateB == null ? rateA : SpendingStatistics.max(rateA, rateB);
        BigDecimal projected = spentToDate.add(rateA.multiply(remainingDays));
        BigDecimal low = spentToDate.add(lowRate.multiply(remainingDays));
        BigDecimal high = spentToDate.add(highRate.multiply(remainingDays));

        Basis basis = new Basis(windowStart, windowDays, windowIncluded.size(), SpendingStatistics.money(rateA),
                rateB == null ? null : SpendingStatistics.money(rateB),
                cap == null ? null : SpendingStatistics.money(cap), capApplied,
                remainingDays.setScale(2, java.math.RoundingMode.HALF_UP));
        return new Result(Status.OK, null, SpendingStatistics.money(spentToDate), SpendingStatistics.money(projected),
                SpendingStatistics.money(low), SpendingStatistics.money(high), basis);
    }
}
