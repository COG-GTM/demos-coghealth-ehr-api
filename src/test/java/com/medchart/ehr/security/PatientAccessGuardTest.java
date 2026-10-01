package com.medchart.ehr.security;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientAccessGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EncounterRepository encounterRepository;

    @InjectMocks
    private PatientAccessGuard guard;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousCallerCannotReachPatientRecords() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(() -> guard.requireAccessToPatient(42L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void unauthenticatedCallerCannotReachPatientRecords() {
        assertThatThrownBy(() -> guard.requireAccessToPatient(42L))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void providerWithoutCareRelationshipIsDenied() {
        authenticate(user(7L, User.Role.PROVIDER));
        when(encounterRepository.existsByPatientIdAndAttendingProviderId(42L, 7L)).thenReturn(false);

        assertThatThrownBy(() -> guard.requireAccessToPatient(42L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void providerWithCareRelationshipIsAllowed() {
        authenticate(user(7L, User.Role.PROVIDER));
        when(encounterRepository.existsByPatientIdAndAttendingProviderId(42L, 7L)).thenReturn(true);

        assertThatCode(() -> guard.requireAccessToPatient(42L)).doesNotThrowAnyException();
    }

    @Test
    void providerNotLinkedToAProviderRecordIsDenied() {
        authenticate(user(null, User.Role.PROVIDER));

        assertThatThrownBy(() -> guard.requireAccessToPatient(42L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminIsAllowed() {
        authenticate(user(null, User.Role.ADMIN));

        assertThatCode(() -> guard.requireAccessToPatient(42L)).doesNotThrowAnyException();
    }

    private User user(Long providerId, User.Role role) {
        return User.builder()
                .id(1L)
                .username("dr.smith")
                .password("x")
                .email("dr.smith@medchart.com")
                .firstName("Dana")
                .lastName("Smith")
                .providerId(providerId)
                .roles(Set.of(role))
                .enabled(true)
                .build();
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
