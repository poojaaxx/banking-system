package com.bankingdemo.adminapi;

import com.bankingdemo.adminapi.dto.AdminAuditLogSummary;
import com.bankingdemo.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditService auditService;

    @GetMapping
    public Page<AdminAuditLogSummary> list(@RequestParam(required = false) String targetType,
                                            @RequestParam(required = false) Long targetId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var results = (targetType != null && targetId != null)
                ? auditService.forTarget(targetType, targetId, pageable)
                : auditService.listAll(pageable);
        return results.map(AdminAuditLogSummary::from);
    }
}
