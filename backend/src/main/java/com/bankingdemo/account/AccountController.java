package com.bankingdemo.account;

import com.bankingdemo.account.dto.AccountResponse;
import com.bankingdemo.account.dto.CreateAccountRequest;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.security.RateLimiter;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/customer/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final RateLimiter rateLimiter;
    private final AppProperties appProperties;

    @GetMapping
    public List<AccountResponse> list() {
        Long customerId = SecurityUtils.requireCustomerId();
        return accountService.listForCustomer(customerId).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        return AccountResponse.from(accountService.getOwned(customerId, id));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        Account account = accountService.create(customerId, request.nickname());
        return ResponseEntity.ok(AccountResponse.from(account));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Void> close(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        accountService.close(customerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/lookup")
    public RecipientLookupResult lookup(@RequestParam String accountNumber) {
        Long customerId = SecurityUtils.requireCustomerId();
        if (!rateLimiter.tryConsume("recipient-lookup:" + customerId,
                appProperties.getRateLimit().getRecipientLookupPerMinute(), Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("Too many lookups. Please wait and try again.");
        }
        return accountService.lookupRecipient(accountNumber);
    }
}
