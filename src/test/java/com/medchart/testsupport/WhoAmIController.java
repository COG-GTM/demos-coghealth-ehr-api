package com.medchart.testsupport;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test-only probe that echoes the SecurityContext as seen by a controller. Lives outside
 * com.medchart.ehr so component scanning never picks it up; tests opt in with @Import.
 */
@RestController
public class WhoAmIController {

    @GetMapping("/test/whoami")
    public Map<String, Object> whoAmI() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", authentication == null ? null : authentication.getClass().getSimpleName());
        body.put("name", authentication == null ? null : authentication.getName());
        body.put("authorities", authentication == null ? null : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
        return body;
    }
}
