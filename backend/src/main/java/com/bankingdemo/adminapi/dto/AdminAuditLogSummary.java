package com.bankingdemo.adminapi.dto;

import com.bankingdemo.audit.AuditLog;

import java.time.Instant;

public record AdminAuditLogSummary(
        Long id, Long adminId, String action, String targetType, Long targetId, String reason, Instant createdAt
) {
    public static AdminAuditLogSummary from(AuditLog a) {
        return new AdminAuditLogSummary(a.getId(), a.getAdminId(), a.getAction(), a.getTargetType(),
                a.getTargetId(), a.getReason(), a.getCreatedAt());
    }
}
