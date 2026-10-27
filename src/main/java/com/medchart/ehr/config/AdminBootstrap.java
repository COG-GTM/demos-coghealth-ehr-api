package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;

/**
 * Creates the first administrator from BOOTSTRAP_ADMIN_* settings. Does nothing
 * unless a password is supplied, and never runs once an enabled admin exists.
 * The check and insert run under a PostgreSQL transaction-scoped advisory lock so
 * instances starting concurrently against one database create at most one admin.
 */
@Component
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    static final long BOOTSTRAP_LOCK_KEY = 0x4D43_4144_4D49_4EL;
    static final String RETIRED_SEED_PASSWORD = "{retired}seeded-admin-credential";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactionOperations;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JdbcTemplate jdbcTemplate,
                          TransactionOperations transactionOperations,
                          @Value("${medchart.bootstrap.admin.username:}") String username,
                          @Value("${medchart.bootstrap.admin.password:}") String password,
                          @Value("${medchart.bootstrap.admin.email:}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionOperations = transactionOperations;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.email = email == null ? "" : email.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasLength(password)) {
            return;
        }
        transactionOperations.executeWithoutResult(status -> {
            jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> null, BOOTSTRAP_LOCK_KEY);
            createFirstAdmin();
        });
    }

    private void createFirstAdmin() {
        if (userRepository.existsEnabledUserWithRole(User.Role.ADMIN)) {
            log.warn("Admin bootstrap skipped: an enabled administrator already exists. "
                    + "Remove BOOTSTRAP_ADMIN_PASSWORD from the environment.");
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(email)) {
            throw new IllegalStateException(
                    "Admin bootstrap requires BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_EMAIL");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.equalsIgnoreCase(username)) {
            throw new IllegalStateException("Admin bootstrap password must be at least "
                    + MIN_PASSWORD_LENGTH + " characters and differ from the username");
        }
        releaseRetiredSeedIdentity();
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "Admin bootstrap refused: username or email already belongs to another account");
        }

        userRepository.save(User.builder()
                .username(username)
                .email(email)
                .firstName("System")
                .lastName("Administrator")
                .password(passwordEncoder.encode(password))
                .roles(Set.of(User.Role.ADMIN))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());

        log.info("Admin bootstrap created administrator '{}'. "
                + "Remove BOOTSTRAP_ADMIN_PASSWORD from the environment.", username);
    }

    // V5 archives the disabled seed account's username/email only when its archival
    // values are free; move any identity it still holds to a unique archival value.
    private void releaseRetiredSeedIdentity() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        jdbcTemplate.update("UPDATE users SET username = 'retired-seed-admin-' || id || '-' || ? "
                + "WHERE password = ? AND enabled = FALSE AND username = ?",
                suffix, RETIRED_SEED_PASSWORD, username);
        jdbcTemplate.update("UPDATE users SET email = 'retired-seed-admin-' || id || '-' || ? || '@invalid' "
                + "WHERE password = ? AND enabled = FALSE AND email = ?",
                suffix, RETIRED_SEED_PASSWORD, email);
    }
}
