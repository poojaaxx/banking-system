package com.bankingdemo.adminapi;

import com.bankingdemo.account.AccountRepository;
import com.bankingdemo.adminapi.dto.AdminAccountSummary;
import com.bankingdemo.adminapi.dto.AdminCustomerDetail;
import com.bankingdemo.adminapi.dto.AdminCustomerSummary;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<AdminCustomerSummary> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return customerRepository.findAll(PageRequest.of(page, Math.min(size, 100))).map(AdminCustomerSummary::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public AdminCustomerDetail get(@PathVariable Long id) {
        Customer customer = customerRepository.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
        var accounts = accountRepository.findByOwnerCustomerIdOrderByCreatedAtAsc(id).stream()
                .map(AdminAccountSummary::from).toList();
        return new AdminCustomerDetail(AdminCustomerSummary.from(customer), accounts);
    }
}
