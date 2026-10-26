package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerRegistrationTest {

    @Test
    void selfRegistrationCannotGrantClinicalAccess() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("pw")).thenReturn("hashed");
        AuthController controller = new AuthController(
                mock(AuthenticationManager.class), users, encoder, mock(JwtTokenProvider.class));

        AuthController.SignUpRequest request = new AuthController.SignUpRequest();
        request.setUsername("mallory");
        request.setEmail("mallory@example.org");
        request.setPassword("pw");
        request.setFirstName("M");
        request.setLastName("X");
        controller.registerUser(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(Set.of(User.Role.STAFF), saved.getValue().getRoles());
        assertFalse(saved.getValue().isEnabled());
    }
}
