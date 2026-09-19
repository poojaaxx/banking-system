package com.bankingdemo.ai.assistant;

import com.bankingdemo.ai.assistant.AssistantAnswerValidator.Rejection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The model may only state numbers, dates and references that exist in the backend context it was given. */
class AssistantAnswerValidatorTest {

    private static final String REF_A = "TXN-11111111-aaaa-bbbb-cccc-000000000001";
    private static final String REF_B = "TXN-22222222-aaaa-bbbb-cccc-000000000002";

    private static AssistantContext context() {
        return new AssistantContext(
                "2026-09-19",
                List.of(new AssistantContext.AccountFact("SAVINGS", "Main", "ACTIVE", "8750.00")),
                "1500.00",
                "820.50",
                List.of(new AssistantContext.CategoryAmountFact("Transport", "250.00")),
                List.of(new AssistantContext.TransactionFact(REF_A, "1250.00", "Shopping", "Laptop bag", "2026-09-12T10:15:30Z"),
                        new AssistantContext.TransactionFact(REF_B, "250.00", "Transport", "Uber ride home", "2026-09-18T18:00:00Z")),
                List.of(new AssistantContext.TransactionFact(REF_B, "250.00", "Transport", "Uber ride home", "2026-09-18T18:00:00Z")));
    }

    private static Optional<Rejection> check(String answer) {
        return AssistantAnswerValidator.validate(answer, context());
    }

    @Test
    void acceptsAnswersThatQuoteBackendNumbersDatesAndReferences() {
        assertThat(check("You spent ₹1500.00 this month and ₹820.50 last month.")).isEmpty();
        assertThat(check("Your largest payment was ₹1,250.00 on 2026-09-12 (" + REF_A + ").")).isEmpty();
        assertThat(check("You have 1 account with a balance of 8750.00.")).isEmpty();
    }

    @Test
    void rejectsAnInventedAmount() {
        assertThat(check("You spent ₹9,999.00 this month.")).contains(Rejection.UNVERIFIED_NUMBER);
    }

    @Test
    void rejectsAModelComputedTotalEvenIfTheArithmeticIsRight() {
        // 1250.00 + 250.00 = 1500.00 is coincidentally a real total, so use one that is not in the data:
        assertThat(check("Your two largest payments add up to ₹1,500.50.")).contains(Rejection.UNVERIFIED_NUMBER);
    }

    @Test
    void rejectsAnInventedDate() {
        assertThat(check("You paid this on 2026-09-25.")).contains(Rejection.UNVERIFIED_NUMBER);
    }

    @Test
    void rejectsPercentagesThatAreNotInTheData() {
        assertThat(check("Transport is 45% of your spending.")).contains(Rejection.UNVERIFIED_NUMBER);
    }

    @Test
    void rejectsAReferenceThatWasNeverInTheContext() {
        assertThat(check("See TXN-99999999-dead-beef-cafe-000000000009 for details.")).contains(Rejection.UNKNOWN_REFERENCE);
    }

    @Test
    void referenceDigitsAreNotMistakenForClaimedNumbers() {
        assertThat(check("The Uber payment was " + REF_B + ".")).isEmpty();
    }

    @Test
    void rejectsLinksMarkdownAndHtml() {
        assertThat(check("Visit https://evil.example to see it.")).contains(Rejection.FORBIDDEN_CONTENT);
        assertThat(check("Click [here](http://x) now")).contains(Rejection.FORBIDDEN_CONTENT);
        assertThat(check("<script>alert(1)</script>")).contains(Rejection.FORBIDDEN_CONTENT);
    }

    @Test
    void rejectsEmptyAndOversizedAnswers() {
        assertThat(check("   ")).contains(Rejection.EMPTY);
        assertThat(check(null)).contains(Rejection.EMPTY);
        assertThat(check("x".repeat(701))).contains(Rejection.TOO_LONG);
    }

    @Test
    void anAnswerWithNoNumbersAtAllIsFine() {
        assertThat(check("I can only answer questions about your own spending.")).isEmpty();
    }
}
