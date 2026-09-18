package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void loginReturnsTokenAndSetsSecurityContext() throws Exception {
        Authentication authentication = new UsernamePasswordAuthenticationToken("dr.smith", "secret");
        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(tokenProvider.generateToken(authentication)).willReturn("jwt-token");

        try {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("username", "dr.smith", "password", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"));

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void loginWithBadCredentialsDoesNotIssueToken() {
        given(authenticationManager.authenticate(any()))
            .willThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", "dr.smith", "password", "wrong")))))
            .hasRootCauseInstanceOf(BadCredentialsException.class);

        verify(tokenProvider, never()).generateToken(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void loginWithMalformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        given(userRepository.existsByUsername("dr.smith")).willReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Username is already taken!"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        given(userRepository.existsByUsername("dr.smith")).willReturn(false);
        given(userRepository.existsByEmail("dr.smith@example.com")).willReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest())))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("Email is already in use!"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerStoresEncodedPasswordWithProviderRole() throws Exception {
        given(userRepository.existsByUsername("dr.smith")).willReturn(false);
        given(userRepository.existsByEmail("dr.smith@example.com")).willReturn(false);
        given(passwordEncoder.encode("secret")).willReturn("encoded-secret");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signUpRequest())))
            .andExpect(status().isOk())
            .andExpect(content().string("User registered successfully"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        User user = saved.getValue();
        assertThat(user.getUsername()).isEqualTo("dr.smith");
        assertThat(user.getEmail()).isEqualTo("dr.smith@example.com");
        assertThat(user.getFirstName()).isEqualTo("Jane");
        assertThat(user.getLastName()).isEqualTo("Smith");
        assertThat(user.getPassword()).isEqualTo("encoded-secret");
        assertThat(user.getRoles()).isEqualTo(Set.of(User.Role.PROVIDER));
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
    }

    @Test
    void registerWithMalformedBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest());

        verify(userRepository, never()).save(any());
    }

    private Map<String, String> signUpRequest() {
        return Map.of(
            "username", "dr.smith",
            "email", "dr.smith@example.com",
            "password", "secret",
            "firstName", "Jane",
            "lastName", "Smith"
        );
    }
}
