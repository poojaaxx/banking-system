package com.bankingdemo.idempotency;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    /** Deterministic SHA-256 hex digest of the canonical JSON form of the given payload. */
    public static String sha256Hex(Object canonicalPayload, ObjectMapper objectMapper) {
        String json = objectMapper.writeValueAsString(canonicalPayload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
