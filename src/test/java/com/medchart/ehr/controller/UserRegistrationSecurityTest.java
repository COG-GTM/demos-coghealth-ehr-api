package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, UserAdminController.class})
@Import(SecurityConfig.class)
class UserRegistrationSecurityTest {

    private static final String REGISTER = "/api/auth/register";
    private static final String VALID_SIGNUP = "{\"username\":\"mallory\",\"email\":\"mallory@example.com\","
        + "\"password\":\"correct-horse-battery\",\"firstName\":\"Mallory\",\"lastName\":\"Outsider\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousRegistrationIsRejectedWith401() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isUnauthorized());

        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminRegistrationIsRejectedWith403() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isForbidden());

        verify(userRepository, never()).save(any());
    }

    @Test
    void tokenForDisabledAdminDoesNotAuthenticate() throws Exception {
        User disabledAdmin = user(1L, "admin", Set.of(User.Role.ADMIN));
        disabledAdmin.setEnabled(false);
        when(tokenProvider.getUsernameFromToken("t")).thenReturn("admin");
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(disabledAdmin);

        mockMvc.perform(post(REGISTER).header("Authorization", "Bearer t")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isUnauthorized());

        verify(userRepository, never()).save(any());
    }

    @Test
    void tokenForEnabledAdminCanRegister() throws Exception {
        when(tokenProvider.getUsernameFromToken("t")).thenReturn("admin");
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(user(1L, "admin", Set.of(User.Role.ADMIN)));

        mockMvc.perform(post(REGISTER).header("Authorization", "Bearer t")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    void adminCreatedAccountGetsStaffNotProvider() throws Exception {
        String withRoleOverride = VALID_SIGNUP.replace("}", ",\"roles\":[\"PROVIDER\",\"ADMIN\"]}");

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(withRoleOverride))
            .andExpect(status().isOk());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).containsExactly(User.Role.STAFF);
        assertThat(saved.getValue().getAuthorities())
            .extracting(Object::toString)
            .containsExactly("ROLE_STAFF")
            .doesNotContain("ROLE_PROVIDER");
        assertThat(passwordEncoder.matches("correct-horse-battery", saved.getValue().getPassword())).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidSignUpPayloadsAreRejected() throws Exception {
        String[] invalid = {
            "{}",
            VALID_SIGNUP.replace("mallory@example.com", "not-an-email"),
            VALID_SIGNUP.replace("correct-horse-battery", "short"),
            VALID_SIGNUP.replace("\"mallory\"", "\"ma llory<script>\""),
            VALID_SIGNUP.replace("\"Mallory\"", "\"   \""),
            VALID_SIGNUP.replace("\"Outsider\"", "\"" + "x".repeat(101) + "\""),
        };
        for (String body : invalid) {
            mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void usernameAndEmailConflictsReturnTheSameGenericMessage() throws Exception {
        when(userRepository.existsByUsername("mallory")).thenReturn(true);
        String byUsername = mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();

        when(userRepository.existsByUsername("mallory")).thenReturn(false);
        when(userRepository.existsByEmail("mallory@example.com")).thenReturn(true);
        String byEmail = mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(VALID_SIGNUP))
            .andExpect(status().isConflict())
            .andReturn().getResponse().getContentAsString();

        assertThat(byUsername).isEqualTo(byEmail).isEqualTo(AuthController.ACCOUNT_CONFLICT_MESSAGE);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginStaysPublic() throws Exception {
        User staff = user(2L, "bob", Set.of(User.Role.STAFF));
        staff.setPassword(passwordEncoder.encode("correct-horse-battery"));
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(staff);
        when(tokenProvider.generateToken(any())).thenReturn("jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"correct-horse-battery\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void anonymousRoleAssignmentIsRejectedWith401() throws Exception {
        mockMvc.perform(put("/api/admin/users/2/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"PROVIDER\"]}"))
            .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = {"STAFF", "PROVIDER"})
    void nonAdminCannotGrantRoles() throws Exception {
        mockMvc.perform(put("/api/admin/users/2/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"]}"))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    void adminExplicitlyGrantsProvider() throws Exception {
        User staff = user(2L, "bob", Set.of(User.Role.STAFF));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));

        mockMvc.perform(put("/api/admin/users/2/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"PROVIDER\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("PROVIDER"));

        verify(userRepository).save(staff);
        assertThat(staff.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_PROVIDER");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void lastAdminCannotBeDemoted() throws Exception {
        User admin = user(1L, "admin", Set.of(User.Role.ADMIN));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countUsableUsersWithRole(User.Role.ADMIN)).thenReturn(1L);

        mockMvc.perform(put("/api/admin/users/1/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"STAFF\"]}"))
            .andExpect(status().isConflict());

        verify(userRepository).lockUsersWithRole(User.Role.ADMIN);
        verify(userRepository, never()).save(any());
        assertThat(admin.getRoles()).containsExactly(User.Role.ADMIN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanBeDemotedWhenAnotherAdminRemains() throws Exception {
        User other = user(5L, "other-admin", Set.of(User.Role.ADMIN));
        when(userRepository.findById(5L)).thenReturn(Optional.of(other));
        when(userRepository.countUsableUsersWithRole(User.Role.ADMIN)).thenReturn(2L);

        mockMvc.perform(put("/api/admin/users/5/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"PROVIDER\"]}"))
            .andExpect(status().isOk());

        verify(userRepository).save(other);
    }

    @Test
    void disabledUserTokenRunsRequestOnceUnauthenticated() throws Exception {
        User disabled = user(3L, "carol", Set.of(User.Role.STAFF));
        disabled.setEnabled(false);
        disabled.setPassword(passwordEncoder.encode("correct-horse-battery"));
        when(tokenProvider.getUsernameFromToken("t")).thenReturn("carol");
        when(tokenProvider.validateToken(anyString(), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("carol")).thenReturn(disabled);
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(staffWithPassword());
        when(tokenProvider.generateToken(any())).thenReturn("jwt");

        mockMvc.perform(post("/api/auth/login").header("Authorization", "Bearer t")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"bob\",\"password\":\"correct-horse-battery\"}"))
            .andExpect(status().isOk());

        verify(tokenProvider, times(1)).generateToken(any());
    }

    @Test
    void loginDoesNotCapLegacyPasswordLength() throws Exception {
        String longPassword = "p".repeat(140);
        User legacy = user(4L, "dave", Set.of(User.Role.STAFF));
        legacy.setPassword("{noop}" + longPassword);
        when(userDetailsService.loadUserByUsername("dave")).thenReturn(legacy);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dave\",\"password\":\"" + longPassword + "\"}"))
            .andExpect(status().is(org.hamcrest.Matchers.not(400)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void roleAssignmentValidatesInput() throws Exception {
        mockMvc.perform(put("/api/admin/users/2/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/2/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"SUPERUSER\"]}"))
            .andExpect(status().isBadRequest());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/admin/users/99/roles").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"STAFF\"]}"))
            .andExpect(status().isNotFound());
        verify(userRepository, never()).save(any());
    }

    private User staffWithPassword() {
        User staff = user(2L, "bob", Set.of(User.Role.STAFF));
        staff.setPassword(passwordEncoder.encode("correct-horse-battery"));
        return staff;
    }

    private static User user(Long id, String username, Set<User.Role> roles) {
        return User.builder()
            .id(id)
            .username(username)
            .password("{noop}unused")
            .email(username + "@example.com")
            .firstName(username)
            .lastName("Test")
            .roles(new HashSet<>(roles))
            .enabled(true)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();
    }
}
