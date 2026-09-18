package com.bankingdemo.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Page<SupportTicket> findByCustomerIdOrderByUpdatedAtDesc(Long customerId, Pageable pageable);

    Optional<SupportTicket> findByCustomerIdAndId(Long customerId, Long id);

    Page<SupportTicket> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Page<SupportTicket> findByStatusOrderByUpdatedAtDesc(TicketStatus status, Pageable pageable);
}
