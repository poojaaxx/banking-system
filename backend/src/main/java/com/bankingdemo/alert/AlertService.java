package com.bankingdemo.alert;

import com.bankingdemo.audit.AuditService;
import com.bankingdemo.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AccountAlertRepository accountAlertRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<AccountAlert> list(boolean onlyUnacknowledged, Pageable pageable) {
        return onlyUnacknowledged
                ? accountAlertRepository.findByAcknowledgedAtIsNullOrderByCreatedAtDesc(pageable)
                : accountAlertRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public AccountAlert acknowledge(Long adminId, Long alertId) {
        AccountAlert alert = accountAlertRepository.findById(alertId)
                .orElseThrow(() -> ApiException.notFound("Alert not found"));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(Instant.now());
            alert.setAcknowledgedByAdminId(adminId);
            accountAlertRepository.save(alert);
            auditService.record(adminId, "ACKNOWLEDGE_ALERT", "ACCOUNT_ALERT", alert.getId(), null);
        }
        return alert;
    }
}
