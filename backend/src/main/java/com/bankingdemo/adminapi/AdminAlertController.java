package com.bankingdemo.adminapi;

import com.bankingdemo.adminapi.dto.AdminAlertSummary;
import com.bankingdemo.alert.AlertService;
import com.bankingdemo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/alerts")
@RequiredArgsConstructor
public class AdminAlertController {

    private final AlertService alertService;

    @GetMapping
    public Page<AdminAlertSummary> list(@RequestParam(defaultValue = "true") boolean onlyUnacknowledged,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return alertService.list(onlyUnacknowledged, PageRequest.of(page, Math.min(size, 100))).map(AdminAlertSummary::from);
    }

    @PostMapping("/{id}/acknowledge")
    public AdminAlertSummary acknowledge(@PathVariable Long id) {
        Long adminId = SecurityUtils.requireAdminId();
        return AdminAlertSummary.from(alertService.acknowledge(adminId, id));
    }
}
