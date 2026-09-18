package com.bankingdemo.ledger;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.account.dto.AccountResponse;
import com.bankingdemo.ledger.dto.CustomerDashboardResponse;
import com.bankingdemo.notification.NotificationRepository;
import com.bankingdemo.notification.RecipientType;
import com.bankingdemo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/customer/dashboard")
@RequiredArgsConstructor
public class CustomerDashboardController {

    private static final int RECENT_TRANSACTION_COUNT = 10;

    private final AccountService accountService;
    private final TransactionHistoryService transactionHistoryService;
    private final NotificationRepository notificationRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public CustomerDashboardResponse dashboard() {
        Long customerId = SecurityUtils.requireCustomerId();

        List<Account> accounts = accountService.listForCustomer(customerId);
        BigDecimal totalBalance = accounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AccountResponse> accountResponses = accounts.stream().map(AccountResponse::from).toList();

        var recent = transactionHistoryService.recentForCustomer(customerId, RECENT_TRANSACTION_COUNT);
        long unread = notificationRepository.countByRecipientTypeAndRecipientIdAndReadFalse(RecipientType.CUSTOMER, customerId);

        return new CustomerDashboardResponse(totalBalance, accountResponses, recent, unread);
    }
}
