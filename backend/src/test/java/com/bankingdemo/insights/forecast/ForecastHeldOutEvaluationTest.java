package com.bankingdemo.insights.forecast;

import com.bankingdemo.insights.forecast.SpendingForecaster.Payment;
import com.bankingdemo.insights.forecast.SpendingForecaster.Result;
import com.bankingdemo.insights.forecast.SpendingForecaster.Status;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chronological held-out evaluation of the month-end projection on SYNTHETIC
 * spending profiles. Every forecast sees only payments made before its cutoff
 * instant, and is then scored against the month's actual total, which the
 * forecaster never sees. Two checks enforce that:
 *  - the forecast is identical whether or not later payments are present, and
 *  - the same forecast is produced from a list truncated at the cutoff.
 *
 * These numbers describe behaviour on generated data only. They are NOT a
 * measure of accuracy on real customers -- there is no real-user data behind
 * them, and docs/insights.md says so.
 */
class ForecastHeldOutEvaluationTest {

    private static final LocalDate DATA_START = LocalDate.parse("2026-02-01");
    private static final LocalDate DATA_END = LocalDate.parse("2026-08-31");
    private static final List<Integer> CUTOFF_DAYS = List.of(8, 15, 22);
    private static final List<Integer> EVALUATED_MONTHS = List.of(4, 5, 6, 7, 8);

    private record Case(String profile, LocalDate cutoff, BigDecimal actual, BigDecimal projected, BigDecimal naive) {
    }

    private static Instant at(LocalDate d, int hour) {
        return d.atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    private static BigDecimal amount(Random rnd, int min, int max) {
        return BigDecimal.valueOf(min + rnd.nextInt(max - min + 1)).setScale(2);
    }

    // ---- synthetic profiles ---------------------------------------------------------------------------

    private static final Map<String, BiFunction<Random, LocalDate, List<Payment>>> PROFILES = new LinkedHashMap<>();

    static {
        // Most days one payment of 150-250.
        PROFILES.put("steady", (rnd, day) -> rnd.nextDouble() < 0.8
                ? List.of(new Payment(at(day, 10), amount(rnd, 150, 250), null)) : List.of());
        // Quiet weekdays, busy weekends.
        PROFILES.put("weekend-heavy", (rnd, day) -> {
            boolean weekend = day.getDayOfWeek().getValue() >= 6;
            double p = weekend ? 0.9 : 0.4;
            return rnd.nextDouble() < p
                    ? List.of(new Payment(at(day, 11), weekend ? amount(rnd, 300, 500) : amount(rnd, 80, 120), null)) : List.of();
        });
        // Steady with occasional 15-25x one-off purchases.
        PROFILES.put("spiky", (rnd, day) -> {
            if (rnd.nextDouble() >= 0.8) {
                return List.of();
            }
            BigDecimal a = rnd.nextDouble() < 0.03 ? amount(rnd, 3000, 5000) : amount(rnd, 150, 250);
            return List.of(new Payment(at(day, 10), a, null));
        });
        // Spending grows ~1% a week.
        PROFILES.put("trending-up", (rnd, day) -> {
            if (rnd.nextDouble() >= 0.8) {
                return List.of();
            }
            double weeks = java.time.temporal.ChronoUnit.DAYS.between(DATA_START, day) / 7.0;
            int base = (int) Math.round(200 * (1 + 0.01 * weeks));
            return List.of(new Payment(at(day, 10), amount(rnd, base - 50, base + 50), null));
        });
        // About one payment every ten days: too thin to project.
        PROFILES.put("sparse", (rnd, day) -> rnd.nextDouble() < 0.1
                ? List.of(new Payment(at(day, 10), amount(rnd, 100, 900), null)) : List.of());
    }

    private static List<Payment> generate(String profile) {
        Random rnd = new Random(20_260_919L + profile.hashCode());
        List<Payment> all = new ArrayList<>();
        for (LocalDate d = DATA_START; !d.isAfter(DATA_END); d = d.plusDays(1)) {
            all.addAll(PROFILES.get(profile).apply(rnd, d));
        }
        return all;
    }

    private static BigDecimal monthTotal(List<Payment> all, int month) {
        return all.stream().filter(p -> p.at().atZone(ZoneOffset.UTC).getMonthValue() == month)
                .map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void chronologicalHeldOutEvaluationOnSyntheticProfiles() throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("SYNTHETIC held-out evaluation of the month-end spending projection (not real-user accuracy)\n");
        report.append("Each forecast uses only payments before its cutoff (day 8/15/22 at 12:00 UTC of months Apr-Aug 2026);\n");
        report.append("APE = |forecast - month actual| / month actual. 'naive' = spent so far / elapsed days x days in month.\n\n");
        report.append(String.format("%-14s %6s %8s %14s %14s %13s%n", "profile", "n", "skipped", "MAPE ours", "MAPE naive", "ours wins"));

        Map<String, Double> oursMape = new LinkedHashMap<>();
        for (String profile : PROFILES.keySet()) {
            List<Payment> all = generate(profile);
            List<Case> cases = new ArrayList<>();
            int skipped = 0;

            for (int month : EVALUATED_MONTHS) {
                BigDecimal actual = monthTotal(all, month);
                for (int cutoffDay : CUTOFF_DAYS) {
                    LocalDate cutoffDate = LocalDate.of(2026, month, cutoffDay);
                    Instant asOf = cutoffDate.atTime(12, 0).toInstant(ZoneOffset.UTC);

                    Result withEverythingPresent = SpendingForecaster.project(all, p -> true, List.of(), asOf, SpendingForecaster.MIN_PAYMENTS,
                            SpendingForecaster.MIN_ACTIVE_DAY_SHARE_TOTAL);
                    List<Payment> knownOnly = all.stream().filter(p -> p.at().isBefore(asOf)).toList();
                    Result knownOnlyResult = SpendingForecaster.project(knownOnly, p -> true, List.of(), asOf, SpendingForecaster.MIN_PAYMENTS,
                            SpendingForecaster.MIN_ACTIVE_DAY_SHARE_TOTAL);

                    // No future leakage: later data must not change the answer.
                    assertThat(withEverythingPresent.status()).isEqualTo(knownOnlyResult.status());
                    if (withEverythingPresent.status() == Status.OK) {
                        assertThat(withEverythingPresent.projected()).isEqualByComparingTo(knownOnlyResult.projected());
                    }

                    if (withEverythingPresent.status() != Status.OK || actual.signum() == 0) {
                        skipped++;
                        continue;
                    }
                    BigDecimal spent = withEverythingPresent.spentToDate();
                    BigDecimal elapsedDays = BigDecimal.valueOf(cutoffDay - 1).add(new BigDecimal("0.5"));
                    BigDecimal naive = spent.multiply(BigDecimal.valueOf(cutoffDate.lengthOfMonth()))
                            .divide(elapsedDays, 2, RoundingMode.HALF_UP);
                    cases.add(new Case(profile, cutoffDate, actual, withEverythingPresent.projected(), naive));
                }
            }

            double ours = mape(cases, true);
            double naive = mape(cases, false);
            long wins = cases.stream().filter(c -> ape(c.projected(), c.actual()) < ape(c.naive(), c.actual())).count();
            oursMape.put(profile, cases.isEmpty() ? Double.NaN : ours);
            report.append(String.format("%-14s %6d %8d %13.1f%% %13.1f%% %8d/%-4d%n", profile, cases.size(), skipped,
                    ours * 100, naive * 100, wins, cases.size()));
        }

        report.append("\nProfiles: steady, weekend-heavy, spiky (3% one-off 15-25x purchases), trending-up (+1%/week), sparse (~1 payment / 10 days).\n");
        Path out = Path.of("target", "forecast-evaluation.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report.toString());
        System.out.println(report);

        // Sanity bounds set from the observed synthetic results (see docs/insights.md), not accuracy claims.
        assertThat(oursMape.get("steady")).isLessThan(0.15);
        assertThat(oursMape.get("weekend-heavy")).isLessThan(0.20);
        assertThat(oursMape.get("spiky")).isLessThan(0.25);
        assertThat(oursMape.get("trending-up")).isLessThan(0.20);
        assertThat(oursMape.get("sparse")).as("sparse history must be refused, not forecast").isNaN();
    }

    private static double ape(BigDecimal forecast, BigDecimal actual) {
        return forecast.subtract(actual).abs().divide(actual, 6, RoundingMode.HALF_UP).doubleValue();
    }

    private static double mape(List<Case> cases, boolean ours) {
        return cases.stream().mapToDouble(c -> ape(ours ? c.projected() : c.naive(), c.actual())).average().orElse(Double.NaN);
    }
}
