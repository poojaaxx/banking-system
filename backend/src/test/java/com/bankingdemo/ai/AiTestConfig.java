package com.bankingdemo.ai;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.alert.AccountAlertRepository;
import com.bankingdemo.alert.AlertRuleRepository;
import com.bankingdemo.alert.UnusualActivityDetector;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.sse.SseEventPublisher;
import com.bankingdemo.testsupport.HistorySeeder;
import com.bankingdemo.testsupport.MutableClock;
import com.bankingdemo.testsupport.RecordingSseEventPublisher;
import com.bankingdemo.testsupport.SwitchableDetector;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;

/**
 * Shared test doubles for every AI / alert / insights test, so they all reuse
 * one Spring context: a scriptable fake instead of the real Groq client (no
 * network, no API key in CI), a pinnable clock, a recording SSE publisher, and
 * a detector that can be made to fail.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(HistorySeeder.class)
public class AiTestConfig {

    @Bean
    @Primary
    public AiChatClient aiChatClient() {
        return new FakeAiChatClient();
    }

    @Bean
    @Primary
    public Clock testClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public SseEventPublisher recordingSseEventPublisher() {
        return new RecordingSseEventPublisher();
    }

    @Bean
    @Primary
    public UnusualActivityDetector switchableDetector(AlertRuleRepository alertRuleRepository,
                                                      AccountAlertRepository accountAlertRepository,
                                                      LedgerEntryRepository ledgerEntryRepository,
                                                      AccountRepository accountRepository,
                                                      NotificationService notificationService) {
        return new SwitchableDetector(alertRuleRepository, accountAlertRepository, ledgerEntryRepository,
                accountRepository, notificationService);
    }
}
