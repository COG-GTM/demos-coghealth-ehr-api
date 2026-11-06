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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerRegistrationTest {

    private UserRepository userRepository;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        authController = new AuthController(
            mock(AuthenticationManager.class),
            userRepository,
            passwordEncoder,
            mock(JwtTokenProvider.class)
        );
    }

    @Test
    void selfRegistrationDoesNotGrantPrivilegedRolesAndLeavesAccountDisabled() {
        when(userRepository.existsByUsername("attacker")).thenReturn(false);
        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);

        ResponseEntity<?> response = authController.registerUser(signUpRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(saved.capture());
        User user = saved.getValue();

        assertThat(user.getRoles()).isEqualTo(Set.of(User.Role.STAFF));
        assertThat(user.getRoles()).doesNotContain(User.Role.PROVIDER, User.Role.ADMIN);
        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void registerEndpointReportsThatTheAccountAwaitsActivation() throws Exception {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content("{\"username\":\"attacker\",\"email\":\"attacker@example.com\","
                    + "\"password\":\"Sup3rSecret!\",\"firstName\":\"Mallory\",\"lastName\":\"Tester\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("disabled").contains("administrator");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).containsExactly(User.Role.STAFF);
        assertThat(saved.getValue().isEnabled()).isFalse();
    }

    private AuthController.SignUpRequest signUpRequest() {
        AuthController.SignUpRequest request = new AuthController.SignUpRequest();
        request.setUsername("attacker");
        request.setEmail("attacker@example.com");
        request.setPassword("Sup3rSecret!");
        request.setFirstName("Mallory");
        request.setLastName("Tester");
        return request;
    }
}
