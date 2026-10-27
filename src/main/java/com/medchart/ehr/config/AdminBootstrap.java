package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/**
 * Sets the initial administrator's password from ADMIN_BOOTSTRAP_PASSWORD so a fresh
 * install has a usable account; does nothing when the variable is unset.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final String ADMIN_USERNAME = "admin";
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapPassword;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${medchart.security.jwt.bootstrap-admin-password:}") String bootstrapPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return;
        }
        if (bootstrapPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        User admin = userRepository.findByUsername(ADMIN_USERNAME).orElseGet(() -> User.builder()
                .username(ADMIN_USERNAME)
                .email("admin@medchart.local")
                .firstName("System")
                .lastName("Administrator")
                .roles(EnumSet.of(User.Role.ADMIN))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build());
        admin.setPassword(passwordEncoder.encode(bootstrapPassword));
        userRepository.save(admin);
        log.info("Bootstrap administrator password set for user '{}'", ADMIN_USERNAME);
    }
}
