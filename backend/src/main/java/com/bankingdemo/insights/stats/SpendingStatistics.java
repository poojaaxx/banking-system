package com.bankingdemo.insights.stats;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Small, dependency-free statistics used by unusual-activity checks and
 * forecasts. Everything is BigDecimal (money never touches double) and pure:
 * the same inputs always give the same outputs, and nothing here reads a
 * clock or a database -- callers decide which data is allowed to be seen.
 *
 * Methods (all standard, documented in docs/insights.md):
 *  - quantile: linear interpolation between closest ranks (a.k.a. "type 7",
 *    the default in R/NumPy/Excel PERCENTILE.INC)
 *  - Tukey fences: Q3 + k * IQR, with k = 3 being the classic "far out" fence
 */
public final class SpendingStatistics {

    private static final MathContext MC = new MathContext(28, RoundingMode.HALF_EVEN);

    private SpendingStatistics() {
    }

    /** @return the p-quantile (0..1) of the values, or null if there are none. */
    public static BigDecimal quantile(Collection<BigDecimal> values, double p) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (p < 0 || p > 1) {
            throw new IllegalArgumentException("p must be within [0, 1]");
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(BigDecimal::compareTo);
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        BigDecimal position = BigDecimal.valueOf(p).multiply(BigDecimal.valueOf(sorted.size() - 1), MC);
        int lower = position.setScale(0, RoundingMode.FLOOR).intValueExact();
        int upper = Math.min(lower + 1, sorted.size() - 1);
        BigDecimal fraction = position.subtract(BigDecimal.valueOf(lower), MC);
        BigDecimal low = sorted.get(lower);
        BigDecimal high = sorted.get(upper);
        return low.add(high.subtract(low, MC).multiply(fraction, MC), MC);
    }

    public static BigDecimal median(Collection<BigDecimal> values) {
        return quantile(values, 0.5);
    }

    /** Q3 + k * (Q3 - Q1); null if there are no values. */
    public static BigDecimal tukeyUpperFence(Collection<BigDecimal> values, double k) {
        BigDecimal q1 = quantile(values, 0.25);
        BigDecimal q3 = quantile(values, 0.75);
        if (q1 == null || q3 == null) {
            return null;
        }
        BigDecimal iqr = q3.subtract(q1, MC);
        return q3.add(iqr.multiply(BigDecimal.valueOf(k), MC), MC);
    }

    public static BigDecimal sum(Collection<BigDecimal> values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            total = total.add(v);
        }
        return total;
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** Rounds to 2 decimal places (paise) for display/storage. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, MC);
    }
}
