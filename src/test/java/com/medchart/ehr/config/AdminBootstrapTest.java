package com.medchart.ehr.config;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private static final String STRONG_PASSWORD = "c0rrect-horse-battery";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private AdminBootstrap bootstrap(String username, String password, String email) {
        return new AdminBootstrap(userRepository, passwordEncoder, jdbcTemplate,
                TransactionOperations.withoutTransaction(), username, password, email);
    }

    @Test
    void doesNothingWithoutPassword() {
        bootstrap("root", "", "root@clinic.test").run(null);

        verifyNoInteractions(userRepository, jdbcTemplate);
    }

    @Test
    void takesAdvisoryLockBeforeCheckingForExistingAdmin() {
        bootstrap("root", STRONG_PASSWORD, "root@clinic.test").run(null);

        InOrder order = inOrder(jdbcTemplate, userRepository);
        order.verify(jdbcTemplate).query(eq("SELECT pg_advisory_xact_lock(?)"),
                any(ResultSetExtractor.class), eq(AdminBootstrap.BOOTSTRAP_LOCK_KEY));
        order.verify(userRepository).existsEnabledUserWithRole(User.Role.ADMIN);
        order.verify(userRepository).save(any());
    }

    @Test
    void createsEnabledAdminWithEncodedPassword() {
        bootstrap(" root ", STRONG_PASSWORD, "root@clinic.test").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("root");
        assertThat(user.getEmail()).isEqualTo("root@clinic.test");
        assertThat(user.getRoles()).containsExactly(User.Role.ADMIN);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPassword()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, user.getPassword())).isTrue();
    }

    @Test
    void refusesWhenEnabledAdminAlreadyExists() {
        when(userRepository.existsEnabledUserWithRole(User.Role.ADMIN)).thenReturn(true);

        bootstrap("root", STRONG_PASSWORD, "root@clinic.test").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        assertThatThrownBy(() -> bootstrap("admin", "admin123", "admin@clinic.test").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("admin123");
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsPasswordEqualToUsername() {
        assertThatThrownBy(() -> bootstrap("administrator1", "Administrator1", "a@clinic.test").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void requiresUsernameAndEmail() {
        assertThatThrownBy(() -> bootstrap("", STRONG_PASSWORD, "root@clinic.test").run(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> bootstrap("root", STRONG_PASSWORD, " ").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesToTakeOverExistingAccount() {
        when(userRepository.existsByUsername("root")).thenReturn(true);

        assertThatThrownBy(() -> bootstrap("root", STRONG_PASSWORD, "root@clinic.test").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }
}
