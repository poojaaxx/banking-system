package com.bankingdemo.recovery;

import com.bankingdemo.TestcontainersConfiguration;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-MySQL tests for the recovery-code security flow: single-use
 * consumption, invalidation on regeneration, and rejection of a wrong or
 * already-used code.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class RecoveryServiceIntegrationTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private RecoveryService recoveryService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = customerService.register("Recovery Test", uniqueEmail(), uniqueUsername(), "correct-horse-battery-x");
    }

    @Test
    void regenerateIssuesTenCodesAndTheyAreAllActive() {
        List<String> codes = recoveryService.regenerate(customer.getId());
        assertThat(codes).hasSize(10);
        assertThat(recoveryService.countActive(customer.getId())).isEqualTo(10);
    }

    @Test
    void redeemingAValidCodeLogsInAndConsumesItPermanently() {
        List<String> codes = recoveryService.regenerate(customer.getId());
        String code = codes.get(0);

        Customer loggedIn = recoveryService.redeemAndLogin(customer.getUsername(), code);
        assertThat(loggedIn.getId()).isEqualTo(customer.getId());
        assertThat(recoveryService.countActive(customer.getId())).isEqualTo(9);

        // The exact same code can never be redeemed again.
        assertThatThrownBy(() -> recoveryService.redeemAndLogin(customer.getUsername(), code))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void aWrongCodeIsRejectedWithoutConsumingAnyRealCode() {
        recoveryService.regenerate(customer.getId());

        assertThatThrownBy(() -> recoveryService.redeemAndLogin(customer.getUsername(), "WRONG-CODE"))
                .isInstanceOf(ApiException.class);
        assertThat(recoveryService.countActive(customer.getId())).isEqualTo(10);
    }

    @Test
    void regeneratingInvalidatesAllPreviouslyIssuedCodes() {
        List<String> firstBatch = recoveryService.regenerate(customer.getId());
        recoveryService.regenerate(customer.getId());

        assertThatThrownBy(() -> recoveryService.redeemAndLogin(customer.getUsername(), firstBatch.get(0)))
                .isInstanceOf(ApiException.class);
        assertThat(recoveryService.countActive(customer.getId())).isEqualTo(10);
    }

    private static String uniqueUsername() {
        return "rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String uniqueEmail() {
        return "rec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "@example.invalid";
    }
}
