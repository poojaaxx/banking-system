package com.bankingdemo.support;

import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.FinancialTransactionRepository;
import com.bankingdemo.notification.NotificationService;
import com.bankingdemo.notification.RecipientType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final NotificationService notificationService;

    @Transactional
    public SupportTicket createTicket(Long customerId, String subject, Long relatedTransactionId, String firstMessage) {
        if (relatedTransactionId != null) {
            financialTransactionRepository.findById(relatedTransactionId)
                    .filter(ft -> ft.getInitiatedByCustomerId().equals(customerId))
                    .orElseThrow(() -> ApiException.badRequest("That transaction does not belong to you"));
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setSubject(subject);
        ticket.setRelatedTransactionId(relatedTransactionId);
        ticket.setStatus(TicketStatus.OPEN);
        ticket = ticketRepository.save(ticket);

        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(SenderType.CUSTOMER);
        message.setSenderId(customerId);
        message.setBody(firstMessage);
        messageRepository.save(message);

        return ticket;
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> listForCustomer(Long customerId, Pageable pageable) {
        return ticketRepository.findByCustomerIdOrderByUpdatedAtDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getOwned(Long customerId, Long ticketId) {
        return ticketRepository.findByCustomerIdAndId(customerId, ticketId)
                .orElseThrow(() -> ApiException.notFound("Support ticket not found"));
    }

    @Transactional(readOnly = true)
    public List<SupportMessage> messagesFor(Long ticketId) {
        return messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public void addCustomerMessage(Long customerId, Long ticketId, String body) {
        SupportTicket ticket = getOwned(customerId, ticketId);
        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(SenderType.CUSTOMER);
        message.setSenderId(customerId);
        message.setBody(body);
        messageRepository.save(message);

        if (ticket.getStatus() == TicketStatus.RESOLVED || ticket.getStatus() == TicketStatus.CLOSED) {
            ticket.setStatus(TicketStatus.OPEN);
            ticketRepository.save(ticket);
        }
    }

    // --- Admin side ---

    @Transactional(readOnly = true)
    public Page<SupportTicket> listAll(TicketStatus status, Pageable pageable) {
        return status == null ? ticketRepository.findAllByOrderByUpdatedAtDesc(pageable)
                : ticketRepository.findByStatusOrderByUpdatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public SupportTicket getAny(Long ticketId) {
        return ticketRepository.findById(ticketId).orElseThrow(() -> ApiException.notFound("Support ticket not found"));
    }

    @Transactional
    public void addAdminResponse(Long adminId, Long ticketId, String body, TicketStatus newStatus) {
        SupportTicket ticket = getAny(ticketId);
        SupportMessage message = new SupportMessage();
        message.setTicketId(ticket.getId());
        message.setSenderType(SenderType.ADMIN);
        message.setSenderId(adminId);
        message.setBody(body);
        messageRepository.save(message);

        if (newStatus != null) {
            ticket.setStatus(newStatus);
            ticketRepository.save(ticket);
        }

        notificationService.create(RecipientType.CUSTOMER, ticket.getCustomerId(), "SUPPORT_REPLY",
                "Support replied to your ticket", "\"" + ticket.getSubject() + "\" has a new reply",
                "SUPPORT_TICKET", ticket.getId());
    }
}
