package com.bankingdemo.customer;

import com.bankingdemo.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class CustomerService {

    public static final int MIN_PASSWORD_LENGTH = 12;
    public static final int MAX_PASSWORD_BYTES = 72; // bcrypt's hard limit; never silently truncated

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Customer register(String fullName, String email, String username, String password) {
        String cleanFullName = requireText(fullName, "Full name");
        String cleanEmail = requireEmail(email);
        String cleanUsername = requireUsername(username);
        validatePasswordPolicy(password);

        if (customerRepository.existsByUsername(cleanUsername)) {
            throw ApiException.conflict("That username is already taken");
        }
        if (customerRepository.existsByEmail(cleanEmail)) {
            throw ApiException.conflict("That email is already registered");
        }

        Customer customer = new Customer();
        customer.setFullName(cleanFullName);
        customer.setEmail(cleanEmail);
        customer.setUsername(cleanUsername);
        customer.setPasswordHash(passwordEncoder.encode(password));
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer changePassword(Long customerId, String currentPassword, String newPassword) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated customer missing from database"));
        if (!passwordEncoder.matches(currentPassword, customer.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        validatePasswordPolicy(newPassword);
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        return customerRepository.save(customer);
    }

    public void validatePasswordPolicy(String password) {
        if (password == null) {
            throw ApiException.badRequest("Password is required");
        }
        int byteLength = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (byteLength > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("Password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 120) {
            throw ApiException.badRequest(fieldName + " is too long");
        }
        return trimmed;
    }

    private String requireUsername(String value) {
        if (value == null || !USERNAME_PATTERN.matcher(value).matches()) {
            throw ApiException.badRequest("Username must be 3-30 characters: letters, numbers, underscore only");
        }
        return value;
    }

    private String requireEmail(String value) {
        if (value == null || value.length() > 190 || !EMAIL_PATTERN.matcher(value.trim()).matches()) {
            throw ApiException.badRequest("A valid email address is required");
        }
        return value.trim().toLowerCase();
    }
}
