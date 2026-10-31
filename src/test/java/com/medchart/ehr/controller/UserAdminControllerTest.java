package com.medchart.ehr.controller;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserAdminController.class)
@Import(SecurityConfig.class)
class UserAdminControllerTest {

    private static final String BODY = "{\"role\":\"STAFF\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private static User pendingUser() {
        return User.builder()
            .id(42L)
            .username("newhire")
            .email("n@example.com")
            .firstName("N")
            .lastName("H")
            .password("hashed")
            .roles(new HashSet<>())
            .enabled(false)
            .accountNonExpired(true)
            .accountNonLocked(true)
            .credentialsNonExpired(true)
            .build();
    }

    @Test
    void anonymousCannotApprove() throws Exception {
        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCannotApprove() throws Exception {
        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminApprovesAndAssignsRole() throws Exception {
        User user = pendingUser();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("STAFF"))
            .andExpect(jsonPath("$.enabled").value(true));

        verify(userRepository).save(user);
        assertTrue(user.isEnabled());
        assertEquals(Set.of(User.Role.STAFF), user.getRoles());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminApprovalRequiresRole() throws Exception {
        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownUserReturnsNotFound() throws Exception {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/users/7/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approvingActiveAccountIsRejectedAndKeepsRoles() throws Exception {
        User admin = pendingUser();
        admin.setEnabled(true);
        admin.setRoles(new HashSet<>(Set.of(User.Role.ADMIN)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(admin));

        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());

        verify(userRepository, never()).save(any());
        assertEquals(Set.of(User.Role.ADMIN), admin.getRoles());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approvingDeactivatedAccountWithRoleIsRejected() throws Exception {
        User deactivated = pendingUser();
        deactivated.setRoles(new HashSet<>(Set.of(User.Role.PROVIDER)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(deactivated));

        mockMvc.perform(post("/api/admin/users/42/approve")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isConflict());
        verify(userRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminListsOnlyPendingAccountsWithIds() throws Exception {
        User deactivated = pendingUser();
        deactivated.setId(43L);
        deactivated.setUsername("former");
        deactivated.setRoles(new HashSet<>(Set.of(User.Role.STAFF)));
        when(userRepository.findByEnabledFalse()).thenReturn(List.of(pendingUser(), deactivated));

        mockMvc.perform(get("/api/admin/users/pending"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(42))
            .andExpect(jsonPath("$[0].username").value("newhire"))
            .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCannotListPendingAccounts() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
            .andExpect(status().isForbidden());
        verify(userRepository, never()).findByEnabledFalse();
    }
}
