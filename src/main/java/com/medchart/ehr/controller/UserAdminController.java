package com.medchart.ehr.controller;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserRepository userRepository;

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveUser(@PathVariable Long id, @Valid @RequestBody ApprovalRequest request) {
        return userRepository.findById(id)
            .map(user -> {
                user.setRoles(new HashSet<>(Set.of(request.getRole())));
                user.setEnabled(true);
                userRepository.save(user);
                log.info("Approved user id={} with role {}", id, request.getRole());
                return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "role", request.getRole().name(),
                    "enabled", true));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static class ApprovalRequest {
        @NotNull
        private User.Role role;

        public User.Role getRole() { return role; }
        public void setRole(User.Role role) { this.role = role; }
    }
}
