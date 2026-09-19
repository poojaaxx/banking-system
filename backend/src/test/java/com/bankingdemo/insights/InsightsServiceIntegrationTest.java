package com.bankingdemo.insights;

import com.bankingdemo.TestcontainersConfiguration;
import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.ai.AiTestConfig;
import com.bankingdemo.budget.Budget;
import com.bankingdemo.budget.BudgetRepository;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerService;
import com.bankingdemo.insights.InsightsResponse.BudgetEstimate;
import com.bankingdemo.insights.InsightsResponse.GoalInsight;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import com.bankingdemo.ledger.TransactionType;
import com.bankingdemo.savingsgoal.GoalStatus;
import com.bankingdemo.savingsgoal.SavingsGoal;
import com.bankingdemo.savingsgoal.SavingsGoalRepository;
import com.bankingdemo.testsupport.HistorySeeder;
import com.bankingdemo.testsupport.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-MySQL tests for the Insights read model. Time is pinned with a
 * controllable clock and history is written with explicit timestamps, so every
 * number here is checkable by hand.
 */
@Import({TestcontainersConfiguration.class, AiTestConfig.class})
@SpringBootTest
@ActiveProfiles("test")
class InsightsServiceIntegrationTest {

    private static final Instant AUG_15_NOON = Instant.parse("2026-08-15T12:00:00Z");

    @Autowired private CustomerService customerService;
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private HistorySeeder seeder;
    @Autowired private InsightsService insightsService;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private SavingsGoalRepository savingsGoalRepository;
    @Autowired private SpendingCategoryRepository categoryRepository;
    @Autowired private Clock clock;

    private Long aId;
    private Long bId;
    private Account aMain;
    private Account bMain;

    @BeforeEach
    void setUp() {
        Customer a = customerService.register("Alice Insights", email(), username(), "correct-horse-battery-1");
        Customer b = customerService.register("Bob Insights", email(), username(), "correct-horse-battery-2");
        aId = a.getId();
        bId = b.getId();
        aMain = accountService.create(aId, "A main");
        bMain = accountService.create(bId, "B main");
        ((MutableClock) clock).pin(AUG_15_NOON);
    }

    @AfterEach
    void tearDown() {
        ((MutableClock) clock).reset();
    }

    private static Instant day(String isoDate, int hour) {
        return LocalDate.parse(isoDate).atTime(hour, 0).toInstant(ZoneOffset.UTC);
    }

    /** One payment per day from `from` to `to` inclusive, paid to Bob's account. */
    private void steadyPayments(Long customerId, Account from, Account to, String fromDate, String toDate, String amount, Long categoryId) {
        for (LocalDate d = LocalDate.parse(fromDate); !d.isAfter(LocalDate.parse(toDate)); d = d.plusDays(1)) {
            seeder.payment(customerId, from, to, TransactionType.TRANSFER, new BigDecimal(amount), d.atTime(10, 0).toInstant(ZoneOffset.UTC),
                    "Payment " + d, categoryId);
        }
    }

    @Test
    void aBrandNewCustomerGetsInsufficientHistoryAndNoInventedNumbers() {
        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.observed().paymentCount()).isZero();
        assertThat(r.observed().grossSpent()).isEqualTo("0.00");
        assertThat(r.projection().status()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(r.projection().reason()).isNotBlank();
        assertThat(r.projection().projectedMonthEnd()).isNull();
        assertThat(r.projection().rangeLow()).isNull();
        assertThat(r.projection().rangeHigh()).isNull();
        assertThat(r.budgets()).isEmpty();
        assertThat(r.goals()).isEmpty();
        assertThat(r.methodology()).isNotEmpty();
    }

    @Test
    void ownAccountTransfersAndSimulatedFundingAreNotSpending() {
        Account aSecond = accountService.create(aId, "A second");
        seeder.deposit(aId, aMain, new BigDecimal("50000.00"), day("2026-08-01", 9));
        seeder.ownTransfer(aId, aMain, aSecond, new BigDecimal("5000.00"), day("2026-08-02", 9), "Move to second");
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("300.00"), day("2026-08-03", 9), "Lunch", null);
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("300.00"), day("2026-08-04", 9), "Dinner", null);
        seeder.withdrawal(aId, aMain, new BigDecimal("100.00"), day("2026-08-05", 9), "Cash", null);

        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.observed().grossSpent()).isEqualTo("700.00");
        assertThat(r.observed().paymentCount()).isEqualTo(3);
    }

    @Test
    void refundsFromAnotherCustomerAreNettedButOrdinaryCreditsAreNot() {
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("2000.00"), day("2026-08-03", 9), "Headphones", null);
        seeder.refund(bId, bMain, aMain, new BigDecimal("500.00"), day("2026-08-05", 9), "Refund for order 12");
        seeder.refund(bId, bMain, aMain, new BigDecimal("800.00"), day("2026-08-06", 9), "Dinner split");

        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.observed().grossSpent()).isEqualTo("2000.00");
        assertThat(r.observed().refundsNetted()).isEqualTo("500.00");
        assertThat(r.observed().netSpent()).isEqualTo("1500.00");
    }

    @Test
    void refundsCanNeverPushObservedSpendingBelowZero() {
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("100.00"), day("2026-08-03", 9), "Small", null);
        seeder.refund(bId, bMain, aMain, new BigDecimal("900.00"), day("2026-08-05", 9), "Refund");

        InsightsResponse r = insightsService.compute(aId);
        assertThat(r.observed().netSpent()).isEqualTo("0.00");
        assertThat(r.observed().refundsNetted()).isEqualTo("100.00");
    }

    @Test
    void steadyHistoryGivesTheHandCheckableProjection() {
        steadyPayments(aId, aMain, bMain, "2026-06-01", "2026-08-14", "200.00", null);

        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.observed().grossSpent()).isEqualTo("2800.00");
        assertThat(r.observed().daysElapsed()).isEqualTo(15);
        assertThat(r.observed().daysInMonth()).isEqualTo(31);
        assertThat(r.projection().status()).isEqualTo("OK");
        assertThat(r.projection().projectedMonthEnd()).isEqualTo("6100.00");
        assertThat(r.projection().basis().dailyRate()).isEqualTo("200.00");
        assertThat(r.projection().basis().windowDays()).isEqualTo(75);
        assertThat(r.projection().assumptions()).isNotEmpty();
    }

    @Test
    void onTheFirstOfTheMonthObservedSpendingRestartsButTheProjectionUsesHistory() {
        steadyPayments(aId, aMain, bMain, "2026-06-05", "2026-08-31", "200.00", null);
        ((MutableClock) clock).pin(Instant.parse("2026-09-01T00:00:00Z"));

        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.observed().monthStart()).isEqualTo("2026-09-01");
        assertThat(r.observed().grossSpent()).isEqualTo("0.00");
        assertThat(r.observed().daysElapsed()).isEqualTo(1);
        assertThat(r.projection().status()).isEqualTo("OK");
        assertThat(r.projection().projectedMonthEnd()).isEqualTo("6000.00");
    }

    @Test
    void paymentsAfterTheClockAreInvisible() {
        steadyPayments(aId, aMain, bMain, "2026-06-01", "2026-08-14", "200.00", null);
        InsightsResponse before = insightsService.compute(aId);

        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("99999.00"), day("2026-08-20", 9), "Future", null);
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("99999.00"), day("2026-09-03", 9), "Next month", null);
        InsightsResponse after = insightsService.compute(aId);

        assertThat(after.observed()).isEqualTo(before.observed());
        assertThat(after.projection()).isEqualTo(before.projection());
    }

    @Test
    void anotherCustomersSpendingNeverChangesMyInsights() {
        steadyPayments(aId, aMain, bMain, "2026-06-01", "2026-08-14", "200.00", null);
        InsightsResponse before = insightsService.compute(aId);

        steadyPayments(bId, bMain, aMain, "2026-06-01", "2026-08-14", "5000.00", null);
        InsightsResponse after = insightsService.compute(aId);

        assertThat(after.observed()).isEqualTo(before.observed());
        assertThat(after.projection()).isEqualTo(before.projection());
        assertThat(insightsService.compute(bId).observed().grossSpent()).isEqualTo("70000.00");
    }

    @Test
    void sparseSpendingIsRefusedNotForecast() {
        for (int i = 0; i < 12; i++) {
            seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("400.00"),
                    Instant.parse("2026-05-27T10:00:00Z").plusSeconds(i * 6L * 86_400L), "Occasional", null);
        }
        InsightsResponse r = insightsService.compute(aId);
        assertThat(r.projection().status()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(r.projection().reason()).contains("too infrequent or irregular");
        assertThat(r.projection().projectedMonthEnd()).isNull();
    }

    @Test
    void budgetsAreEstimatedPerCategoryWithHonestStatuses() {
        Long dining = categoryRepository.findByCode("DINING").orElseThrow().getId();
        Long transport = categoryRepository.findByCode("TRANSPORT").orElseThrow().getId();
        Long utilities = categoryRepository.findByCode("UTILITIES").orElseThrow().getId();
        Long groceries = categoryRepository.findByCode("GROCERIES").orElseThrow().getId();
        LocalDate august = LocalDate.parse("2026-08-01");

        steadyPayments(aId, aMain, bMain, "2026-06-01", "2026-08-14", "100.00", dining);        // Aug so far 1400, pace 100/day
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("600.00"), day("2026-08-02", 9), "Cab", transport);
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("50.00"), day("2026-07-10", 9), "Power", utilities);
        seeder.payment(aId, aMain, bMain, TransactionType.TRANSFER, new BigDecimal("50.00"), day("2026-08-10", 9), "Power", utilities);
        steadyPayments(aId, aMain, bMain, "2026-06-02", "2026-08-13", "30.00", groceries);

        budget(aId, dining, august, "3000.00");     // projected 1400 + 100*16.5 = 3050 -> over by 50
        budget(aId, transport, august, "500.00");   // already 600 spent
        budget(aId, utilities, august, "400.00");   // only 2 payments -> insufficient
        budget(aId, groceries, august, "100000.00");// far below the limit

        InsightsResponse r = insightsService.compute(aId);

        BudgetEstimate d = budgetFor(r, "Dining & Food");
        assertThat(d.status()).isEqualTo("PROJECTED_OVER");
        assertThat(d.projectedMonthEnd()).isEqualTo("3050.00");
        assertThat(d.estimatedOverrun()).isEqualTo("50.00");
        assertThat(budgetFor(r, "Transport").status()).isEqualTo("ALREADY_OVER");
        assertThat(budgetFor(r, "Utilities").status()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(budgetFor(r, "Utilities").projectedMonthEnd()).isNull();
        assertThat(budgetFor(r, "Groceries").status()).isEqualTo("ON_TRACK");
    }

    @Test
    void savingsGoalsShowObservedProgressAndOnlyProjectWhenTheHistorySupportsIt() {
        Instant created = Instant.parse("2026-02-10T09:00:00Z");

        // (a) Steady 1,000/month into the goal for Apr-Jul: 4,000 saved of 10,000.
        Account goalA = goalAccount(aId, "Laptop", "4000.00");
        goal(aId, "Laptop", "10000.00", LocalDate.parse("2027-01-15"), goalA, GoalStatus.ACTIVE, created);
        for (String m : new String[]{"2026-04", "2026-05", "2026-06", "2026-07"}) {
            seeder.ownTransfer(aId, aMain, goalA, new BigDecimal("1000.00"), Instant.parse(m + "-15T10:00:00Z"), "Savings goal: Laptop");
        }
        // (b) Nothing ever contributed.
        Account goalB = goalAccount(aId, "Trip", "0.00");
        goal(aId, "Trip", "5000.00", null, goalB, GoalStatus.ACTIVE, created);
        // (c) Created five days ago: too new for any trend, but the arithmetic still works.
        Account goalC = goalAccount(aId, "Bike", "500.00");
        goal(aId, "Bike", "6500.00", LocalDate.parse("2027-02-15"), goalC, GoalStatus.ACTIVE, Instant.parse("2026-08-10T09:00:00Z"));
        // (d) Only simulated deposits went into it: funding is not a contribution habit.
        Account goalD = goalAccount(aId, "Deposits only", "3000.00");
        goal(aId, "Deposits only", "9000.00", null, goalD, GoalStatus.ACTIVE, created);
        for (String m : new String[]{"2026-05", "2026-06", "2026-07"}) {
            seeder.deposit(aId, goalD, new BigDecimal("1000.00"), Instant.parse(m + "-15T10:00:00Z"));
        }
        // (e) Already reached, and (f) closed goals.
        Account goalE = goalAccount(aId, "Done", "2000.00");
        goal(aId, "Done", "2000.00", null, goalE, GoalStatus.COMPLETED, created);
        Account goalF = goalAccount(aId, "Closed", "10.00");
        goal(aId, "Closed", "999.00", null, goalF, GoalStatus.CLOSED, created);

        InsightsResponse r = insightsService.compute(aId);

        assertThat(r.goals()).extracting(GoalInsight::name).containsExactlyInAnyOrder("Laptop", "Trip", "Bike", "Deposits only", "Done");

        GoalInsight laptop = goalNamed(r, "Laptop");
        assertThat(laptop.saved()).isEqualTo("4000.00");
        assertThat(laptop.remaining()).isEqualTo("6000.00");
        assertThat(laptop.percentComplete()).isEqualTo("40.0");
        assertThat(laptop.projection().status()).isEqualTo("OK");
        assertThat(laptop.projection().averageMonthlyContribution()).isEqualTo("1000.00");
        assertThat(laptop.projection().completeMonthsUsed()).isEqualTo(3);
        assertThat(laptop.projection().estimatedCompletionMonth()).isEqualTo("2027-02"); // Aug + 6 months
        assertThat(laptop.projection().assumptions()).isNotEmpty();
        assertThat(laptop.requiredMonthlyForTargetDate()).isNotNull();

        GoalInsight trip = goalNamed(r, "Trip");
        assertThat(trip.projection().status()).isEqualTo("NO_POSITIVE_TREND");
        assertThat(trip.projection().estimatedCompletionMonth()).isNull();

        GoalInsight bike = goalNamed(r, "Bike");
        assertThat(bike.projection().status()).isEqualTo("INSUFFICIENT_HISTORY");
        assertThat(bike.projection().estimatedCompletionMonth()).isNull();
        assertThat(bike.saved()).isEqualTo("500.00");
        assertThat(bike.requiredMonthlyForTargetDate()).isNotNull();

        assertThat(goalNamed(r, "Deposits only").projection().status()).isEqualTo("NO_POSITIVE_TREND");
        assertThat(goalNamed(r, "Done").projection().status()).isEqualTo("COMPLETED");
    }

    @Test
    void anotherCustomersGoalsAreNeverShown() {
        Account goalB = goalAccount(bId, "Bob's goal", "100.00");
        goal(bId, "Bob's goal", "1000.00", null, goalB, GoalStatus.ACTIVE, Instant.parse("2026-02-10T09:00:00Z"));
        assertThat(insightsService.compute(aId).goals()).isEmpty();
        assertThat(insightsService.compute(bId).goals()).hasSize(1);
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private Account goalAccount(Long customerId, String name, String balance) {
        Account account = accountService.create(customerId, "Goal: " + name);
        account.setBalance(new BigDecimal(balance));
        return accountRepository.save(account);
    }

    private void goal(Long customerId, String name, String target, LocalDate targetDate, Account account, GoalStatus status, Instant createdAt) {
        SavingsGoal goal = new SavingsGoal();
        goal.setCustomerId(customerId);
        goal.setName(name);
        goal.setTargetAmount(new BigDecimal(target));
        goal.setTargetDate(targetDate);
        goal.setLinkedAccountId(account.getId());
        goal.setStatus(status);
        goal.setCreatedAt(createdAt);
        savingsGoalRepository.save(goal);
    }

    private void budget(Long customerId, Long categoryId, LocalDate monthStart, String limit) {
        Budget b = new Budget();
        b.setCustomerId(customerId);
        b.setCategoryId(categoryId);
        b.setMonthStart(monthStart);
        b.setLimitAmount(new BigDecimal(limit));
        budgetRepository.save(b);
    }

    private static BudgetEstimate budgetFor(InsightsResponse r, String category) {
        return r.budgets().stream().filter(b -> b.category().equals(category)).findFirst().orElseThrow();
    }

    private static GoalInsight goalNamed(InsightsResponse r, String name) {
        return r.goals().stream().filter(g -> g.name().equals(name)).findFirst().orElseThrow();
    }

    private static String username() {
        return "ins_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String email() {
        return "ins_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.invalid";
    }
}
