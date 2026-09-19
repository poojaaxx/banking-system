package com.bankingdemo.insights.forecast;

import com.bankingdemo.insights.forecast.GoalProjector.Flow;
import com.bankingdemo.insights.forecast.GoalProjector.Result;
import com.bankingdemo.insights.forecast.GoalProjector.Status;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalProjectorTest {

    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");

    private static Flow flow(String isoInstant, String amount) {
        return new Flow(Instant.parse(isoInstant), new BigDecimal(amount));
    }

    private static List<Flow> monthly(String amount, String... months) {
        List<Flow> flows = new ArrayList<>();
        for (String m : months) {
            flows.add(flow(m + "-15T10:00:00Z", amount));
        }
        return flows;
    }

    @Test
    void reachedTargetIsCompleteAndNothingIsProjected() {
        Result r = GoalProjector.project(new BigDecimal("5000"), new BigDecimal("5000"),
                Instant.parse("2026-03-10T00:00:00Z"), null, List.of(), NOW);
        assertThat(r.status()).isEqualTo(Status.COMPLETED);
        assertThat(r.estimatedCompletionMonth()).isNull();
    }

    @Test
    void aRecentGoalHasInsufficientHistoryButStillGetsTheArithmeticRequiredMonthlyAmount() {
        // Created Aug 25: August is partial and September is in progress -> zero complete months.
        Result r = GoalProjector.project(new BigDecimal("12000"), new BigDecimal("2000"),
                Instant.parse("2026-08-25T00:00:00Z"), LocalDate.parse("2027-01-19"), monthly("2000", "2026-08"), NOW);
        assertThat(r.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
        assertThat(r.estimatedCompletionMonth()).isNull();
        // 10,000 remaining over 122 days (~4.008 months) - a plain calculation, not a forecast.
        assertThat(r.requiredMonthlyForTargetDate()).isEqualByComparingTo("2494.88");
        assertThat(r.onPaceForTargetDate()).isNull();
    }

    @Test
    void positiveTrendGivesAnEstimatedCompletionMonthNotADate() {
        // Complete months are Apr-Aug; the last three (Jun, Jul, Aug) contributed 1,000 each -> 1,000/month.
        Result r = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), null,
                monthly("1000", "2026-04", "2026-05", "2026-06", "2026-07", "2026-08"), NOW);
        assertThat(r.status()).isEqualTo(Status.OK);
        assertThat(r.monthsUsed()).isEqualTo(3);
        assertThat(r.averageMonthlyContribution()).isEqualByComparingTo("1000.00");
        assertThat(r.estimatedCompletionMonth()).isEqualTo(YearMonth.of(2027, 4)); // Sep + 7 months
    }

    @Test
    void noContributionsMeansNoPositiveTrendAndNoDate() {
        Result r = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), null, List.of(), NOW);
        assertThat(r.status()).isEqualTo(Status.NO_POSITIVE_TREND);
        assertThat(r.estimatedCompletionMonth()).isNull();
    }

    @Test
    void netWithdrawalsAreNotAPositiveTrendEither() {
        List<Flow> flows = new ArrayList<>(monthly("1000", "2026-06"));
        flows.addAll(monthly("-1500", "2026-07", "2026-08"));
        Result r = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), null, flows, NOW);
        assertThat(r.status()).isEqualTo(Status.NO_POSITIVE_TREND);
        assertThat(r.averageMonthlyContribution().signum()).isNegative();
    }

    @Test
    void onPaceIsComparedAgainstTheRequiredMonthlyAmount() {
        Result slow = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), LocalDate.parse("2027-01-19"),
                monthly("1000", "2026-06", "2026-07", "2026-08"), NOW);
        assertThat(slow.onPaceForTargetDate()).isFalse();

        Result fast = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), LocalDate.parse("2027-01-19"),
                monthly("2500", "2026-06", "2026-07", "2026-08"), NOW);
        assertThat(fast.onPaceForTargetDate()).isTrue();
    }

    @Test
    void contributionsAfterTheAsOfInstantAreIgnored() {
        List<Flow> flows = new ArrayList<>(monthly("1000", "2026-06", "2026-07", "2026-08"));
        flows.add(flow("2026-10-05T00:00:00Z", "50000"));
        Result r = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), null, flows, NOW);
        assertThat(r.averageMonthlyContribution()).isEqualByComparingTo("1000.00");
    }

    @Test
    void aTargetDateInThePastGivesNoRequiredAmount() {
        Result r = GoalProjector.project(new BigDecimal("10000"), new BigDecimal("3000"),
                Instant.parse("2026-03-10T00:00:00Z"), LocalDate.parse("2026-06-01"),
                monthly("1000", "2026-06", "2026-07", "2026-08"), NOW);
        assertThat(r.requiredMonthlyForTargetDate()).isNull();
        assertThat(r.onPaceForTargetDate()).isNull();
    }
}
