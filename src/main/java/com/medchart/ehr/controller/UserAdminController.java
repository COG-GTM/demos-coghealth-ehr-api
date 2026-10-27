package com.medchart.ehr.controller;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public static class RoleAssignmentRequest {
        @NotEmpty
        private Set<User.@NotNull Role> roles;

        public Set<User.Role> getRoles() { return roles; }
        public void setRoles(Set<User.Role> roles) { this.roles = roles; }
    }
}
