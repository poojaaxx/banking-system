package com.bankingdemo.insights.forecast;

import com.bankingdemo.insights.forecast.SpendingForecaster.Payment;
import com.bankingdemo.insights.forecast.SpendingForecaster.Result;
import com.bankingdemo.insights.forecast.SpendingForecaster.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingForecasterTest {

    private static final Instant AUG_15_NOON = Instant.parse("2026-08-15T12:00:00Z");

    private static Instant at(String date, String time) {
        return Instant.parse(date + "T" + time + "Z");
    }

    /** One payment of `amount` at 10:00 on every day from `from` through `to` inclusive. */
    private static List<Payment> daily(String from, String to, String amount, Long categoryId) {
        List<Payment> list = new ArrayList<>();
        for (LocalDate d = LocalDate.parse(from); !d.isAfter(LocalDate.parse(to)); d = d.plusDays(1)) {
            list.add(new Payment(d.atTime(10, 0).toInstant(ZoneOffset.UTC), new BigDecimal(amount), categoryId));
        }
        return list;
    }

    private static Result total(List<Payment> payments, List<Payment> refunds, Instant asOf) {
        return SpendingForecaster.project(payments, p -> true, refunds, asOf, SpendingForecaster.MIN_PAYMENTS,
                SpendingForecaster.MIN_ACTIVE_DAY_SHARE_TOTAL);
    }

    @Test
    void noHistoryIsInsufficientAndInventsNothing() {
        Result r = total(List.of(), List.of(), AUG_15_NOON);
        assertThat(r.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(r.projected()).isNull();
        assertThat(r.low()).isNull();
        assertThat(r.high()).isNull();
        assertThat(r.spentToDate()).isEqualByComparingTo("0");
    }

    @Test
    void fewerThanFourteenDaysOfHistoryIsInsufficient() {
        Result r = total(daily("2026-08-05", "2026-08-14", "200", null), List.of(), AUG_15_NOON);
        assertThat(r.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(r.reason()).contains("day(s) of history");
        assertThat(r.spentToDate()).isEqualByComparingTo("2000");
    }

    @Test
    void fewerThanFivePaymentsIsInsufficientEvenIfTheWindowIsLong() {
        List<Payment> sparse = List.of(
                new Payment(at("2026-07-01", "10:00:00"), new BigDecimal("300"), null),
                new Payment(at("2026-07-20", "10:00:00"), new BigDecimal("300"), null),
                new Payment(at("2026-08-05", "10:00:00"), new BigDecimal("300"), null));
        Result r = total(sparse, List.of(), AUG_15_NOON);
        assertThat(r.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(r.reason()).contains("payment(s)");
    }

    @Test
    void steadyHistoryProjectsSpentSoFarPlusDailyRateTimesRemainingTime() {
        // 75 days of 200/day (Jun 1 - Aug 14). Aug 1-14 = 2800 spent. Remaining = 16 whole days + half of today.
        Result r = total(daily("2026-06-01", "2026-08-14", "200", null), List.of(), AUG_15_NOON);
        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.spentToDate()).isEqualByComparingTo("2800.00");
        assertThat(r.basis().windowDays()).isEqualTo(75);
        assertThat(r.basis().dailyRate()).isEqualByComparingTo("200.00");
        assertThat(r.basis().weeklyMedianDailyRate()).isEqualByComparingTo("200.00");
        assertThat(r.basis().remainingDays()).isEqualByComparingTo("16.50");
        assertThat(r.projected()).isEqualByComparingTo("6100.00");
        // Both methods agree here, so the range collapses to the point estimate.
        assertThat(r.low()).isEqualByComparingTo("6100.00");
        assertThat(r.high()).isEqualByComparingTo("6100.00");
    }

    @Test
    void dataAtOrAfterTheAsOfInstantIsNeverUsed() {
        List<Payment> history = daily("2026-06-01", "2026-08-14", "200", null);
        Result withoutFuture = total(history, List.of(), AUG_15_NOON);

        List<Payment> polluted = new ArrayList<>(history);
        polluted.add(new Payment(AUG_15_NOON, new BigDecimal("999999"), null));                       // exactly at asOf
        polluted.add(new Payment(at("2026-08-20", "09:00:00"), new BigDecimal("999999"), null));      // future
        polluted.add(new Payment(at("2026-09-02", "09:00:00"), new BigDecimal("999999"), null));      // next month
        Result withFuture = total(polluted, List.of(new Payment(at("2026-08-30", "09:00:00"), new BigDecimal("50000"), null)), AUG_15_NOON);

        assertThat(withFuture.projected()).isEqualByComparingTo(withoutFuture.projected());
        assertThat(withFuture.spentToDate()).isEqualByComparingTo(withoutFuture.spentToDate());
        assertThat(withFuture.basis().dailyRate()).isEqualByComparingTo(withoutFuture.basis().dailyRate());
    }

    @Test
    void monthBoundaryOnTheFirstStartsFromZeroAndUsesTheWholeMonthRemaining() {
        // Midnight on Sep 1: August payments are history, not month-to-date.
        Instant sep1 = at("2026-09-01", "00:00:00");
        Result r = total(daily("2026-06-05", "2026-08-31", "200", null), List.of(), sep1);
        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.spentToDate()).isEqualByComparingTo("0.00");
        assertThat(r.basis().remainingDays()).isEqualByComparingTo("30.00"); // 29 whole days after today + all of today
        assertThat(r.projected()).isEqualByComparingTo("6000.00");
    }

    @Test
    void lastSecondOfMonthLeavesNoRemainingTimeSoProjectionEqualsObserved() {
        Instant lastSecond = at("2026-08-31", "23:59:59");
        Result r = total(daily("2026-06-01", "2026-08-30", "200", null), List.of(), lastSecond);
        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.basis().remainingDays().doubleValue()).isLessThan(0.01);
        assertThat(r.projected().subtract(r.spentToDate()).doubleValue()).isLessThan(1.0);
    }

    @Test
    void aSingleOneOffSpikeIsCappedRatherThanExtrapolatedAcrossTheMonth() {
        List<Payment> history = new ArrayList<>(daily("2026-06-01", "2026-08-14", "200", null));
        history.add(new Payment(at("2026-07-10", "15:00:00"), new BigDecimal("100000"), null));
        Result r = total(history, List.of(), AUG_15_NOON);

        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.basis().capApplied()).isTrue();
        assertThat(r.basis().outlierCap()).isEqualByComparingTo("200.00");
        // Without capping the daily rate would be ~1,533/day; capped it stays near 200.
        assertThat(r.basis().dailyRate().doubleValue()).isBetween(200.0, 210.0);
    }

    @Test
    void refundsReduceMonthToDateSpendingAndTheDailySeries() {
        List<Payment> refunds = List.of(new Payment(at("2026-08-10", "08:00:00"), new BigDecimal("500"), null));
        Result r = total(daily("2026-06-01", "2026-08-14", "200", null), refunds, AUG_15_NOON);

        assertThat(r.spentToDate()).isEqualByComparingTo("2300.00"); // 2800 - 500
        // Aug 10's 200 is netted to zero (never negative): 14800 / 75 days.
        assertThat(r.basis().dailyRate()).isEqualByComparingTo("197.33");
    }

    @Test
    void aRefundLargerThanTheMonthsSpendingNeverMakesSpendingNegative() {
        List<Payment> refunds = List.of(new Payment(at("2026-08-12", "08:00:00"), new BigDecimal("999999"), null));
        Result r = total(daily("2026-06-01", "2026-08-14", "200", null), refunds, AUG_15_NOON);
        assertThat(r.spentToDate()).isEqualByComparingTo("0.00");
        assertThat(r.projected().signum()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void aCategoryWithTooFewPaymentsIsInsufficientWhileTheTotalIsProjected() {
        List<Payment> history = new ArrayList<>(daily("2026-06-01", "2026-08-14", "200", 1L));
        history.add(new Payment(at("2026-07-01", "12:00:00"), new BigDecimal("80"), 7L));
        history.add(new Payment(at("2026-07-15", "12:00:00"), new BigDecimal("80"), 7L));

        Result category = SpendingForecaster.project(history, p -> Long.valueOf(7L).equals(p.categoryId()), List.of(),
                AUG_15_NOON, SpendingForecaster.MIN_PAYMENTS_PER_CATEGORY, SpendingForecaster.MIN_ACTIVE_DAY_SHARE_CATEGORY);
        Result all = total(history, List.of(), AUG_15_NOON);

        assertThat(category.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(all.status()).isEqualTo(Status.OK);
    }

    @Test
    void whenWeeklyAndDailyMethodsDisagreeTheRangeSpansBoth() {
        // Mostly 100/day, but a regular two-week stretch at 600/day (19% of days: a pattern, not a one-off,
        // so it is not capped). Mean daily spend (A) is higher than the median week (B).
        List<Payment> history = new ArrayList<>();
        history.addAll(daily("2026-06-01", "2026-07-05", "100", null));
        history.addAll(daily("2026-07-06", "2026-07-19", "600", null));
        history.addAll(daily("2026-07-20", "2026-08-14", "100", null));
        Result r = total(history, List.of(), AUG_15_NOON);

        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.basis().capApplied()).isFalse();
        assertThat(r.basis().weeklyMedianDailyRate()).isEqualByComparingTo("100.00");
        assertThat(r.low()).isLessThan(r.high());
        assertThat(r.projected()).isEqualByComparingTo(r.high()); // the main estimate is the A-based one
    }

    @Test
    void irregularInfrequentSpendingIsRefusedEvenWithEnoughPayments() {
        // 12 payments over 80 days: enough payments, but only ~15% of days are active.
        List<Payment> history = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            history.add(new Payment(at("2026-05-27", "10:00:00").plusSeconds(i * 6L * 86_400L), new BigDecimal("400"), null));
        }
        Result r = total(history, List.of(), AUG_15_NOON);
        assertThat(r.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(r.reason()).contains("too infrequent or irregular");
        assertThat(r.projected()).isNull();
    }
}
