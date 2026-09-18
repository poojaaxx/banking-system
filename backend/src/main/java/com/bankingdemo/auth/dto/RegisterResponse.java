package com.bankingdemo.auth.dto;

import java.util.List;

public record RegisterResponse(CustomerSessionInfo customer, List<String> recoveryCodes) {
}
