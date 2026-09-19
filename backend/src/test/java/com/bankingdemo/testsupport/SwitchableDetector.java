package com.bankingdemo.testsupport;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.alert.AccountAlertRepository;
import com.bankingdemo.alert.AlertRuleRepository;
import com.bankingdemo.alert.UnusualActivityDetector;
import com.bankingdemo.ledger.FinancialTransaction;
import com.bankingdemo.ledger.LedgerEntryRepository;
import com.bankingdemo.notification.NotificationService;

/** The real detector, unless a test makes it fail -- to prove a detector bug can never fail a money movement. */
public class SwitchableDetector extends UnusualActivityDetector {

    private volatile boolean failing;

    public SwitchableDetector(AlertRuleRepository alertRuleRepository, AccountAlertRepository accountAlertRepository,
                              LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository,
                              NotificationService notificationService) {
        super(alertRuleRepository, accountAlertRepository, ledgerEntryRepository, accountRepository, notificationService);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    @Override
    public void evaluate(FinancialTransaction tx) {
        if (failing) {
            throw new IllegalStateException("simulated detector bug");
        }
        super.evaluate(tx);
    }
}
