package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("BR-SVC-22: known users are loaded for authentication")
    void brSvc22_knownUserLoaded() {
        User u = new User();
        u.setUsername("clinician1");
        when(userRepository.findByUsername("clinician1")).thenReturn(Optional.of(u));
        assertSame(u, service.loadUserByUsername("clinician1"));
    }

    @Test
    @DisplayName("BR-SVC-22: unknown usernames are rejected with UsernameNotFoundException")
    void brSvc22_unknownUserRejected() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        UsernameNotFoundException e = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));
        assertEquals("User not found with username: ghost", e.getMessage());
    }
}
