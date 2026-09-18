package com.bankingdemo.ledger;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ReferenceGenerator {

    public String generate() {
        return "TXN-" + UUID.randomUUID();
    }
}
