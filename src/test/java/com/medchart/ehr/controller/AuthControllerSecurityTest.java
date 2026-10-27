package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtAuthenticationFilter;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AuthControllerSecurityTest {

    private static final String SIGNUP = "{\"username\":\"mallory\",\"email\":\"m@example.com\","
            + "\"password\":\"pw\",\"firstName\":\"M\",\"lastName\":\"X\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousLoginIsPermittedAndIssuesTokenWithDefaultSecretConfig() throws Exception {
        User admin = User.builder().username("admin").password(passwordEncoder.encode("admin123"))
                .email("a@example.com").firstName("A").lastName("B").roles(Set.of(User.Role.ADMIN))
                .enabled(true).accountNonExpired(true).accountNonLocked(true).credentialsNonExpired(true).build();
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(admin);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminRegistrationIsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRegisterUsers() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(SIGNUP))
                .andExpect(status().isOk());
        verify(userRepository).save(any());
    }
}
