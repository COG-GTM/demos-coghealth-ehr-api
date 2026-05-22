package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        User user = User.builder()
                .id(1L)
                .username("dr.anderson")
                .password("encoded_password")
                .email("anderson@hospital.com")
                .firstName("Sarah")
                .lastName("Anderson")
                .roles(Set.of(User.Role.PROVIDER))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();

        when(userRepository.findByUsername("dr.anderson")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("dr.anderson");

        assertNotNull(result);
        assertEquals("dr.anderson", result.getUsername());
        assertTrue(result.isEnabled());
    }

    @Test
    void loadUserByUsername_nonExistingUser_throwsUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
    }
}
