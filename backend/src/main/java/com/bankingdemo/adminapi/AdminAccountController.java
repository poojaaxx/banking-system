package com.bankingdemo.adminapi;

import com.bankingdemo.account.AdminAccountService;
import com.bankingdemo.adminapi.dto.AdminAccountSummary;
import com.bankingdemo.adminapi.dto.FreezeRequest;
import com.bankingdemo.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @GetMapping
    public Page<AdminAccountSummary> list(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return adminAccountService.search(status, PageRequest.of(page, Math.min(size, 100))).map(AdminAccountSummary::from);
    }

    @GetMapping("/{id}")
    public AdminAccountSummary get(@PathVariable Long id) {
        return AdminAccountSummary.from(adminAccountService.getAny(id));
    }

    @PostMapping("/{id}/freeze")
    public AdminAccountSummary freeze(@PathVariable Long id, @Valid @RequestBody FreezeRequest request) {
        Long adminId = SecurityUtils.requireAdminId();
        return AdminAccountSummary.from(adminAccountService.freeze(adminId, id, request.reason()));
    }

    @PostMapping("/{id}/unfreeze")
    public AdminAccountSummary unfreeze(@PathVariable Long id, @Valid @RequestBody FreezeRequest request) {
        Long adminId = SecurityUtils.requireAdminId();
        return AdminAccountSummary.from(adminAccountService.unfreeze(adminId, id, request.reason()));
    }
}
