package com.bankingdemo.support.dto;

import java.util.List;

public record SupportTicketDetail(SupportTicketResponse ticket, List<SupportMessageResponse> messages) {
}
