package com.bankingdemo.adminapi;

import com.bankingdemo.security.SecurityUtils;
import com.bankingdemo.support.SupportService;
import com.bankingdemo.support.SupportTicket;
import com.bankingdemo.support.TicketStatus;
import com.bankingdemo.support.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportController {

    private final SupportService supportService;

    @GetMapping
    public Page<SupportTicketResponse> list(@RequestParam(required = false) TicketStatus status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return supportService.listAll(status, PageRequest.of(page, Math.min(size, 100))).map(SupportTicketResponse::from);
    }

    @GetMapping("/{id}")
    public SupportTicketDetail get(@PathVariable Long id) {
        SupportTicket ticket = supportService.getAny(id);
        return new SupportTicketDetail(SupportTicketResponse.from(ticket),
                supportService.messagesFor(ticket.getId()).stream().map(SupportMessageResponse::from).toList());
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<Void> respond(@PathVariable Long id, @Valid @RequestBody AdminRespondRequest request) {
        Long adminId = SecurityUtils.requireAdminId();
        supportService.addAdminResponse(adminId, id, request.message(), request.newStatus());
        return ResponseEntity.noContent().build();
    }
}
