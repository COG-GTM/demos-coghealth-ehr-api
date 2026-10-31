package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthControllerRegistrationTest {

    private AuthenticationManager authenticationManager;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        controller = new AuthController(authenticationManager, userRepository, passwordEncoder,
            mock(JwtTokenProvider.class));
    }

    private static AuthController.SignUpRequest signUp() {
        AuthController.SignUpRequest req = new AuthController.SignUpRequest();
        req.setUsername("mallory");
        req.setEmail("m@example.com");
        req.setPassword("x");
        req.setFirstName("M");
        req.setLastName("L");
        return req;
    }

    @Test
    void selfRegisteredAccountHasNoRoleAndIsDisabled() {
        ResponseEntity<?> response = controller.registerUser(signUp());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertTrue(user.getRoles().isEmpty(), "self-registration must not grant any role");
        assertFalse(user.getRoles().contains(User.Role.PROVIDER));
        assertFalse(user.isEnabled(), "self-registered account must await admin approval");
        assertTrue(user.getAuthorities().isEmpty());
        assertEquals("hashed", user.getPassword());
    }

    @Test
    void duplicateUsernameIsRejectedWithoutSaving() {
        when(userRepository.existsByUsername("mallory")).thenReturn(true);

        ResponseEntity<?> response = controller.registerUser(signUp());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginForPendingAccountReturnsForbidden() {
        when(authenticationManager.authenticate(any(Authentication.class)))
            .thenThrow(new DisabledException("User is disabled"));
        AuthController.LoginRequest login = new AuthController.LoginRequest();
        login.setUsername("mallory");
        login.setPassword("x");

        ResponseEntity<?> response = controller.authenticateUser(login);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
