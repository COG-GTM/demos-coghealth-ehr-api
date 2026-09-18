package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsernameReturnsUserWithRolesWhenFound() {
        User user = User.builder()
                .id(1L)
                .username("dr.house")
                .password("encoded-password")
                .email("dr.house@example.com")
                .firstName("Gregory")
                .lastName("House")
                .roles(Set.of(User.Role.PROVIDER, User.Role.ADMIN))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        when(userRepository.findByUsername("dr.house")).thenReturn(Optional.of(user));

        UserDetails loaded = customUserDetailsService.loadUserByUsername("dr.house");

        assertThat(loaded).isSameAs(user);
        assertThat(loaded.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("ROLE_PROVIDER", "ROLE_ADMIN");
    }

    @Test
    void loadUserByUsernameThrowsWhenNotFound() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing");
    }
}
