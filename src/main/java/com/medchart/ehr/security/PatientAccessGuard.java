package com.medchart.ehr.security;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Object-level authorization for patient records: callers must be authenticated,
 * and a provider may only reach patients they have a care relationship with.
 */
@Component
@RequiredArgsConstructor
public class PatientAccessGuard {

    private final UserRepository userRepository;
    private final EncounterRepository encounterRepository;

    public User requireAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authentication is required to access patient records");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Unknown principal for patient access"));
    }

    public void requireAccessToPatient(Long patientId) {
        User user = requireAuthenticatedUser();
        if (hasRole(user, User.Role.ADMIN) || hasRole(user, User.Role.STAFF)) {
            return;
        }

        Long providerId = user.getProviderId();
        if (providerId != null
                && encounterRepository.existsByPatientIdAndAttendingProviderId(patientId, providerId)) {
            return;
        }

        throw new AccessDeniedException("Not authorized to access this patient record");
    }

    private boolean hasRole(User user, User.Role role) {
        return user.getRoles() != null && user.getRoles().contains(role);
    }
}
