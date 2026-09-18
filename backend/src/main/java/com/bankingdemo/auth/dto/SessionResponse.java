package com.bankingdemo.auth.dto;

public record SessionResponse(boolean authenticated, String role, CustomerSessionInfo customer, AdminSessionInfo admin) {

    public static SessionResponse anonymous() {
        return new SessionResponse(false, null, null, null);
    }

    public static SessionResponse forCustomer(CustomerSessionInfo customer) {
        return new SessionResponse(true, "CUSTOMER", customer, null);
    }

    public static SessionResponse forAdmin(AdminSessionInfo admin) {
        return new SessionResponse(true, "ADMIN", null, admin);
    }
}
