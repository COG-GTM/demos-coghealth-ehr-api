package com.medchart.ehr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.medchart.ehr.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorApiTest extends AbstractApiIntegrationTest {

    @Test
    void healthIsUpWithoutDetailsForAnonymousCallers() {
        ResponseEntity<JsonNode> response = getJson("/actuator/health");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fieldNames(response.getBody())).containsExactly("status");
        assertThat(response.getBody().get("status").asText()).isEqualTo("UP");
    }

    @Test
    void onlyHealthInfoAndMetricsAreExposed() {
        assertThat(getJson("/actuator/info").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson("/actuator/metrics").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getJson("/actuator/env").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getJson("/actuator/beans").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
