package com.bankingdemo.support;

import com.bankingdemo.security.SecurityUtils;
import com.bankingdemo.support.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer/support/tickets")
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping
    public Page<SupportTicketResponse> list(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        Long customerId = SecurityUtils.requireCustomerId();
        return supportService.listForCustomer(customerId, PageRequest.of(page, Math.min(size, 100)))
                .map(SupportTicketResponse::from);
    }

    @GetMapping("/{id}")
    public SupportTicketDetail get(@PathVariable Long id) {
        Long customerId = SecurityUtils.requireCustomerId();
        SupportTicket ticket = supportService.getOwned(customerId, id);
        return new SupportTicketDetail(SupportTicketResponse.from(ticket),
                supportService.messagesFor(ticket.getId()).stream().map(SupportMessageResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<SupportTicketResponse> create(@Valid @RequestBody CreateTicketRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        SupportTicket ticket = supportService.createTicket(customerId, request.subject(),
                request.relatedTransactionId(), request.message());
        return ResponseEntity.ok(SupportTicketResponse.from(ticket));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<Void> addMessage(@PathVariable Long id, @Valid @RequestBody AddMessageRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        supportService.addCustomerMessage(customerId, id, request.message());
        return ResponseEntity.noContent().build();
    }
}
