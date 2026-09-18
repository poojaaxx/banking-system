package com.bankingdemo.security;

import com.bankingdemo.admin.Admin;
import com.bankingdemo.admin.AdminRepository;
import com.bankingdemo.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Bootstraps exactly one admin account from runtime environment variables on
 * first startup. There is no default admin, no password reset on a normal
 * restart (if admins already exist, this is a no-op), and no credentials are
 * ever logged. If two instances started concurrently both attempted this at
 * once, the unique constraint on admins.username/email makes the loser's
 * insert fail harmlessly -- see CLAUDE.md.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 72;

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (adminRepository.count() > 0) {
            log.info("Admin bootstrap skipped: at least one admin already exists.");
            return;
        }

        AppProperties.AdminBootstrap bootstrap = appProperties.getAdminBootstrap();
        String username = bootstrap.getUsername();
        String email = bootstrap.getEmail();
        String password = bootstrap.getPassword();

        if (isBlank(username) || isBlank(email) || isBlank(password)) {
            log.warn("No admin exists yet and ADMIN_BOOTSTRAP_USERNAME/EMAIL/PASSWORD are not fully set. " +
                    "Set them and restart to create the first admin. See docs/admin-bootstrap.md.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.getBytes().length > MAX_PASSWORD_LENGTH) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD does not meet the password policy ({}-{} bytes). Admin was not created.",
                    MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH);
            return;
        }

        Admin admin = new Admin();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));

        try {
            adminRepository.save(admin);
            log.info("Bootstrapped initial admin account '{}'.", username);
        } catch (DataIntegrityViolationException e) {
            log.info("Admin bootstrap raced with another instance; an admin already exists now.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
