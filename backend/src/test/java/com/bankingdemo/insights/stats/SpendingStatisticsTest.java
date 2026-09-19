package com.bankingdemo.insights.stats;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpendingStatisticsTest {

    private static List<BigDecimal> nums(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void medianOfOddAndEvenCounts() {
        assertThat(SpendingStatistics.median(nums("5", "1", "3"))).isEqualByComparingTo("3");
        assertThat(SpendingStatistics.median(nums("1", "2", "3", "4"))).isEqualByComparingTo("2.5");
    }

    @Test
    void quantileInterpolatesLinearlyBetweenClosestRanks() {
        List<BigDecimal> oneToFive = nums("1", "2", "3", "4", "5");
        assertThat(SpendingStatistics.quantile(oneToFive, 0.25)).isEqualByComparingTo("2");
        assertThat(SpendingStatistics.quantile(oneToFive, 0.75)).isEqualByComparingTo("4");
        assertThat(SpendingStatistics.quantile(nums("10", "20"), 0.5)).isEqualByComparingTo("15");
        assertThat(SpendingStatistics.quantile(oneToFive, 0)).isEqualByComparingTo("1");
        assertThat(SpendingStatistics.quantile(oneToFive, 1)).isEqualByComparingTo("5");
    }

    @Test
    void tukeyFarOutFenceIsQ3PlusKTimesIqr() {
        // Q1=2, Q3=4, IQR=2 -> 4 + 3*2 = 10
        assertThat(SpendingStatistics.tukeyUpperFence(nums("1", "2", "3", "4", "5"), 3.0)).isEqualByComparingTo("10");
    }

    @Test
    void identicalValuesGiveAFenceEqualToThatValue() {
        assertThat(SpendingStatistics.tukeyUpperFence(nums("200", "200", "200", "200"), 3.0)).isEqualByComparingTo("200");
    }

    @Test
    void emptyInputGivesNullNotAnInventedNumber() {
        assertThat(SpendingStatistics.median(List.of())).isNull();
        assertThat(SpendingStatistics.tukeyUpperFence(List.of(), 3.0)).isNull();
    }

    @Test
    void quantileRejectsOutOfRangeP() {
        assertThatThrownBy(() -> SpendingStatistics.quantile(nums("1"), 1.5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moneyRoundsHalfUpToTwoPlaces() {
        assertThat(SpendingStatistics.money(new BigDecimal("2.005")).toPlainString()).isEqualTo("2.01");
        assertThat(SpendingStatistics.money(new BigDecimal("2")).toPlainString()).isEqualTo("2.00");
    }
}
