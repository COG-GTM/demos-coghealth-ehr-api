package com.medchart.ehr.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Boots the full application on a random port (context path /api) against Testcontainers
 * Postgres + Redis, so status codes and error bodies come from the real servlet container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractApiIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        TestContainers.register(registry);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    protected static String unique(String prefix) {
        return prefix + SEQUENCE.incrementAndGet();
    }

    protected ResponseEntity<JsonNode> getJson(String path) {
        return rest.getForEntity(path, JsonNode.class);
    }

    protected ResponseEntity<JsonNode> sendJson(HttpMethod method, String path, String body) {
        return rest.exchange(path, method, jsonEntity(body), JsonNode.class);
    }

    protected ResponseEntity<String> sendJsonForString(HttpMethod method, String path, String body) {
        return rest.exchange(path, method, jsonEntity(body), String.class);
    }

    protected static HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    protected static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
