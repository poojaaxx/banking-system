package com.bankingdemo.moneyrequest;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "money_requests")
@Getter
@Setter
@NoArgsConstructor
public class MoneyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_customer_id", nullable = false)
    private Long requesterCustomerId;

    @Column(name = "requester_account_id", nullable = false)
    private Long requesterAccountId;

    @Column(name = "payer_customer_id", nullable = false)
    private Long payerCustomerId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoneyRequestStatus status = MoneyRequestStatus.PENDING;

    @Column(name = "financial_transaction_id")
    private Long financialTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
