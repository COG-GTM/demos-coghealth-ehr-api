package com.medchart.ehr.persistence;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import com.medchart.ehr.support.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractJpaTest {

    @Autowired
    private UserRepository users;

    @Test
    void seedAdminHasAdminRoleAndIsEnabled() {
        User admin = users.findByUsername("admin").orElseThrow();

        assertThat(admin.getRoles()).containsExactly(User.Role.ADMIN);
        assertThat(admin.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.isAccountNonLocked()).isTrue();
        assertThat(users.existsByEmail("admin@medchart.com")).isTrue();
        assertThat(users.findByEmail("admin@medchart.com")).isPresent();
    }

    @Test
    void seedAdminHashMatchesNeitherDocumentedNorCommonPassword() {
        String hash = users.findByUsername("admin").orElseThrow().getPassword();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        assertThat(hash).startsWith("$2a$10$").hasSize(60);
        assertThat(encoder.matches("admin123", hash)).isFalse();
        assertThat(encoder.matches("password", hash)).isFalse();
    }
}
