# Insights, unusual-activity checks and forecasts

Everything on the **Insights** page and every unusual-activity flag is computed
by backend code (`com.bankingdemo.insights`, `com.bankingdemo.alert`) with
standard statistics on the customer's own simulated transactions. **No language
model is involved in any number, date, flag or projection.** Nothing here is
"AI fraud detection", and nothing here ever freezes an account, blocks or
reverses a payment, or moves money.

## Definitions (used everywhere)

| Term | Meaning |
| --- | --- |
| Spending | A debit from one of the customer's own accounts, **except** a transfer between two of their own accounts (this also excludes savings-goal contributions). Withdrawals, bill payments and transfers to other customers count. |
| Simulated funding | Deposits. They are credits, so they are never spending, and they are **never treated as income, salary or a recurring pattern**. There is no income forecast at all. |
| Refund | A credit from another customer's account whose description contains "refund" or "reversal". The ledger has no first-class refund type, so this description match is the documented, deliberately conservative rule. |
| Month | The UTC calendar month. Dates and months from the API are UTC; the UI never shifts them into the viewer's zone. |

Refunds reduce the amount on the day they arrive and never take a day (or the
month-to-date total) below zero.

## Unusual-activity checks

Rules live in `alert_rules` (Flyway `V10`), so thresholds are data, not code.
Both run inside the money-movement transaction, on the transaction just
written, and only when it is genuine customer spending (not a deposit, not a
system account, not an own-account transfer).

| Rule | Fires when | Thresholds |
| --- | --- | --- |
| `UNUSUAL_LARGE_SPEND` | The payment is at least the absolute floor **and** above both the Tukey far-out fence (Q3 + 3 × IQR) **and** 3 × the median of the customer's earlier payments | floor ₹1,000 (`threshold_amount`); needs ≥ 5 earlier payments (`threshold_count`); look-back 90 days (`window_minutes` = 129600). The 3.0 constants are in `UnusualActivityDetector`. |
| `REPEATED_PAYMENT` | This payment makes ≥ N identical payments (same recipient account, same amount) within the window; transfers and bill payments only | N = 3 (`threshold_count`) within 24 h (`window_minutes` = 1440) |

The older demo rules (`LARGE_TRANSACTION`, `RAPID_TRANSFERS`,
`REPEATED_FAILED_LOGIN`) are unchanged; this work extends the same
`alert_rules` / `account_alerts` tables and the admin alerts page.

**Only earlier information is used.** The baseline query takes payments with a
smaller transaction id **and** an earlier timestamp than the payment being
judged, so a check gives the same answer when replayed later
(`UnusualActivityIntegrationTest.theBaselineOnlyUsesInformationThatExistedBeforeTheTransaction`
adds 25 later large payments and shows the earlier payment is still judged as
it was).

**Cold start.** With fewer than 5 earlier payments nothing is flagged; the
customer simply has no baseline yet.

**Deduplication.** Alerts are written with `INSERT IGNORE` against a unique
`(rule_code, dedupe_key)`; large-spend alerts key on the transaction, repeat
alerts additionally suppress further alerts for the same recipient/amount
cluster inside the window. A duplicate can never throw, so it can never fail
the transfer. Five identical transfers produce one alert and one notification
(`UnusualActivityIntegrationTest`, and the packaged-browser `insights.spec.ts`).

**Delivery.** A notification is created through the existing
`NotificationService`, addressed to the owning customer only; the SSE push
happens after the transaction commits, so a rolled-back payment produces no
phantom alert. Customers read their own flags at
`GET /api/customer/insights/alerts` (filtered by the authenticated customer id,
never by a client-supplied id). Admins see them in the existing alerts page.

**Labelling.** Messages say "Unusual activity (statistical check)" or
"(repeat-payment check)", show the numbers behind the flag, and state "not a
finding of fraud; nothing was blocked".

**Safety.** If the detector throws for any reason, the error is logged (class
name only) and swallowed; the transfer still commits
(`aBugInTheDetectorCanNeverFailOrRollBackAMoneyMovement`).

**Known limits.** Baselines read at most the latest 1,000 payments in the
window. The check runs synchronously in the payment transaction (a handful of
indexed reads). Rules are per customer; there is no cross-customer or
device/location signal. These are prompts to look, tuned by judgment, not
validated against real fraud data.

## Month-end projection

`SpendingForecaster` is a pure function: payments in, projection out, no
database and no clock. Anything at or after the "as of" instant is ignored, so
it cannot use future data.

1. **History window**: every complete UTC day from the customer's first
   payment in the last 90 days up to yesterday; days with no spending count as
   zero.
2. **Gates → "Insufficient history"** (no number is shown): fewer than 14 days
   of history, fewer than 10 payments in the window, or payments on fewer than
   25% of the window's days (10% for a single category). The last gate exists
   because held-out testing showed lumpy, infrequent spending cannot be
   extrapolated as a daily rate.
3. **One-off spikes**: with ≥ 8 payments, each payment is capped at the Tukey
   far-out fence — but only if at most 10% of payments exceed it. If more do,
   large payments are a regular pattern, not one-offs, and nothing is capped.
4. **Estimate A** = (capped, refund-netted spend) ÷ window days.
   **Estimate B** = median whole-week total ÷ 7 (needs ≥ 2 whole weeks).
5. **Projected month-end** = spent so far this month (net of refunds) +
   A × remaining time in the month (whole days after today plus the unelapsed
   fraction of today).
6. **Range** = the projection using min(A, B) to the projection using
   max(A, B). It is the spread between two estimation methods. It is **not** a
   confidence interval; no probability is calculated, so none is shown.

Month boundaries: at 00:00:00 UTC on the 1st, spending so far is ₹0 and the
whole month remains; at the last second of the month no time remains, so the
projection equals what was observed.

### Budget estimates

Per budget category (current month): `ALREADY_OVER` (spent ≥ limit),
`PROJECTED_OVER` (main estimate passes the limit; overrun = projection − limit),
`POSSIBLY_OVER` (only the higher estimate passes it), `ON_TRACK`, or
`INSUFFICIENT_HISTORY` (needs ≥ 3 payments in that category and 10% active
days). Category projections are computed but **not separately evaluated** in
the held-out fixtures below.

### Savings goals

Progress (saved, remaining, percent) is observed fact: the real balance of the
goal's dedicated account. A completion **month** is estimated only when at
least 2 complete calendar months exist after the goal's creation month and the
average **net** monthly contribution over the last (up to) 3 of them is
positive. Contributions are transfers into the goal minus transfers out;
simulated deposits/withdrawals on the goal account are excluded. Otherwise the
status is `INSUFFICIENT_HISTORY` or `NO_POSITIVE_TREND` and no date is given.
The "required per month for the target date" figure is plain arithmetic
(remaining ÷ months left), labelled as such. The estimate is "around
*Month YYYY* if contributions continue at that average" — never a guaranteed
date, never with a confidence figure.

## Evaluation on chronological held-out fixtures (synthetic)

`ForecastHeldOutEvaluationTest` generates deterministic synthetic spending
profiles (Feb–Aug 2026), forecasts each month from day 8, 15 and 22 (12:00 UTC,
Apr–Aug) using only payments before that instant, and scores against the
month's actual total. It also asserts that the forecast is identical whether or
not later payments exist. Results from the run on 2026-09-19:

| Profile | Forecasts made (of 15) | MAPE — this method | MAPE — naive run-rate |
| --- | --- | --- | --- |
| steady | 15 | 5.9% | 10.2% |
| weekend-heavy | 15 | 7.1% | 8.2% |
| spiky (3% one-off 15–25× purchases) | 15 | 13.0% | 21.0% |
| trending-up (+1%/week) | 15 | 7.3% | 11.4% |
| sparse (~1 payment / 10 days) | 0 (all refused) | — | — |

"Naive run-rate" = spent so far ÷ elapsed days × days in month.

**These are results on generated data only.** They show the method behaves
sensibly and beats a naive baseline on those shapes; they are **not** a
measurement of accuracy for real customers, of which there is no data. Two
findings changed the design: the first version (≥ 5 payments) still forecast
the sparse profile, with 52–80% error, which motivated the 10-payment and
25%-active-days gates; and testing a "busy fortnight" pattern showed the spike
cap wrongly flattened regular heavy periods, which motivated the "at most 10%
of payments" rule.

## Assumptions and limitations shown to customers

The Insights page states, next to every projection, the assumptions used, the
basis (window, daily rate, cap) and that estimates are not guarantees. There is
no seasonality, no scheduled/recurring-bill detection, and no anticipation of
large upcoming bills.
