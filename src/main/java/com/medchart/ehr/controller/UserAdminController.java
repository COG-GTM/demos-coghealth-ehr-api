package com.medchart.ehr.controller;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class UserAdminController {

    private final UserRepository userRepository;

    @PutMapping("/{id}/roles")
    @Transactional
    public ResponseEntity<?> setRoles(@PathVariable Long id,
                                      @Valid @RequestBody RoleAssignmentRequest request,
                                      Authentication currentUser) {
        return userRepository.findById(id)
            .<ResponseEntity<?>>map(user -> {
                Set<User.Role> previous = user.getRoles();
                if (removesAdmin(previous, request.getRoles()) && isLastUsableAdmin(user)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Cannot remove ADMIN from the last active administrator");
                }
                user.setRoles(EnumSet.copyOf(request.getRoles()));
                userRepository.save(user);
                log.info("Admin {} changed roles of user {} from {} to {}",
                    currentUser.getName(), user.getUsername(), previous, user.getRoles());

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", user.getId());
                body.put("username", user.getUsername());
                body.put("roles", user.getRoles());
                return ResponseEntity.ok(body);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean removesAdmin(Set<User.Role> previous, Set<User.Role> requested) {
        return previous != null && previous.contains(User.Role.ADMIN) && !requested.contains(User.Role.ADMIN);
    }

    /** Locks every ADMIN row first so concurrent demotions serialize, then re-counts committed state. */
    private boolean isLastUsableAdmin(User target) {
        userRepository.lockUsersWithRole(User.Role.ADMIN);
        boolean targetCounts = target.isEnabled() && target.isAccountNonLocked() && target.isAccountNonExpired();
        return targetCounts && userRepository.countUsableUsersWithRole(User.Role.ADMIN) <= 1;
    }

    public static class RoleAssignmentRequest {
        @NotEmpty
        private Set<User.@NotNull Role> roles;

        public Set<User.Role> getRoles() { return roles; }
        public void setRoles(Set<User.Role> roles) { this.roles = roles; }
    }
}
