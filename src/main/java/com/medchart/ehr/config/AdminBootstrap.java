package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * Creates the first administrator from BOOTSTRAP_ADMIN_* settings. Does nothing
 * unless a password is supplied, and never runs once an enabled admin exists.
 */
@Component
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final String email;

    public AdminBootstrap(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${medchart.bootstrap.admin.username:}") String username,
                          @Value("${medchart.bootstrap.admin.password:}") String password,
                          @Value("${medchart.bootstrap.admin.email:}") String email) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.email = email == null ? "" : email.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasLength(password)) {
            return;
        }
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
}
