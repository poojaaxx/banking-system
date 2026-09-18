package com.bankingdemo.adminapi;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.account.AccountType;
import com.bankingdemo.adminapi.dto.AdminDashboardSummary;
import com.bankingdemo.alert.AccountAlertRepository;
import com.bankingdemo.customer.CustomerRepository;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.support.SupportTicketRepository;
import com.bankingdemo.support.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real backend aggregates only -- system accounts are excluded from both the
 * account count and the balance total (sumAllCustomerBalances already scopes
 * to account_type = 'SAVINGS').
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final AccountAlertRepository accountAlertRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public AdminDashboardSummary summary() {
        return new AdminDashboardSummary(
                customerRepository.count(),
                accountRepository.countByAccountType(AccountType.SAVINGS),
                accountRepository.sumAllCustomerBalances(),
                financialTransactionRepository.count(),
                supportTicketRepository.countByStatus(TicketStatus.OPEN),
                accountAlertRepository.countByAcknowledgedAtIsNull());
    }
}
