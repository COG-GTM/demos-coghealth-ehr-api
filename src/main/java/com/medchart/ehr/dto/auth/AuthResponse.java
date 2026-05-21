package com.medchart.ehr.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private final String type = "Bearer";
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
