package com.bankingdemo.billpay;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
    Page<BillPayment> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
}
