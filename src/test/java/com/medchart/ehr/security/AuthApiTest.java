package com.medchart.ehr.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** AuthController is mapped to /api/auth under the /api context path, i.e. /api/api/auth/**. */
class AuthApiTest extends AbstractApiIntegrationTest {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void registerThenLoginIssuesBearerToken() {
        String username = unique("user");
        assertThat(register(username, username + "@example.com").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> login = login(username, "S3cret!pass");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fieldNames(login.getBody())).containsExactlyInAnyOrder("token", "type");
        assertThat(login.getBody().get("type").asText()).isEqualTo("Bearer");
        String token = login.getBody().get("token").asText();
        assertThat(tokenProvider.getUsernameFromToken(token)).isEqualTo(username);
        assertThat(tokenProvider.getClaimFromToken(token, claims -> claims.get("roles")).toString())
                .contains("ROLE_PROVIDER");
    }

    @Test
    void registerStoresBcryptHashAndDefaultProviderRole() {
        String username = unique("hash");
        ResponseEntity<String> response = register(username, username + "@example.com");

        assertThat(response.getBody()).isEqualTo("User registered successfully");
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE username = ?", String.class, username))
                .startsWith("$2a$10$").hasSize(60);
        List<String> roles = jdbc.queryForList(
                "SELECT r.role FROM user_roles r JOIN users u ON u.id = r.user_id WHERE u.username = ?",
                String.class, username);
        assertThat(roles).containsExactly("PROVIDER");
    }

    @Test
    void registerRejectsDuplicateUsernameAndEmailWithPlainText400() {
        String username = unique("dupe");
        register(username, username + "@example.com");

        ResponseEntity<String> sameUsername = register(username, unique("other") + "@example.com");
        assertThat(sameUsername.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sameUsername.getBody()).isEqualTo("Username is already taken!");

        ResponseEntity<String> sameEmail = register(unique("other"), username + "@example.com");
        assertThat(sameEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sameEmail.getBody()).isEqualTo("Email is already in use!");
    }

    @Test
    void registerWithoutPasswordIsA500() {
        String username = unique("nopass");
        ResponseEntity<String> response = sendJsonForString(HttpMethod.POST, "/api/auth/register",
                "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\","
                        + "\"firstName\":\"No\",\"lastName\":\"Pass\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void badCredentialsAreRejectedWith403() {
        String username = unique("wrongpw");
        register(username, username + "@example.com");

        ResponseEntity<JsonNode> wrongPassword = login(username, "not-the-password");
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(wrongPassword.getBody().get("path").asText()).isEqualTo("/api/api/auth/login");

        assertThat(login(unique("ghost"), "whatever").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void seededAdminCannotLogIn() {
        assertThat(login("admin", "admin123").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login("admin", "password").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void authEndpointsAreNotServedWithoutTheDoubledApiPrefix() {
        ResponseEntity<JsonNode> response = rest.exchange("http://localhost:" + port() + "/api/auth/login",
                HttpMethod.POST, jsonEntity("{\"username\":\"a\",\"password\":\"b\"}"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> register(String username, String email) {
        return sendJsonForString(HttpMethod.POST, "/api/auth/register",
                "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"S3cret!pass\","
                        + "\"firstName\":\"Test\",\"lastName\":\"User\"}");
    }

    private ResponseEntity<JsonNode> login(String username, String password) {
        return sendJson(HttpMethod.POST, "/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private int port() {
        return Integer.parseInt(rest.getRootUri().replaceAll("^.*:(\\d+).*$", "$1"));
    }
}
