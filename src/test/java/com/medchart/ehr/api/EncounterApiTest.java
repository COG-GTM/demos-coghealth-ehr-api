package com.medchart.ehr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.medchart.ehr.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encounter endpoints return JPA entities directly, so their JSON shape and the lazy-proxy
 * serialization failures below are exactly what a Jackson/Hibernate upgrade can change.
 */
class EncounterApiTest extends AbstractApiIntegrationTest {

    private long encounterId;
    private String encounterNumber;

    @BeforeEach
    void insertScheduledEncounter() {
        encounterNumber = unique("ENC-T-");
        jdbc.update("INSERT INTO encounters (encounter_number, patient_id, attending_provider_id, encounter_type, "
                        + "status, encounter_date_time, notes) VALUES (?, 3, 2, 'OFFICE_VISIT', 'SCHEDULED', "
                        + "'2031-02-03 10:00:00', 'initial')",
                encounterNumber);
        encounterId = jdbc.queryForObject(
                "SELECT id FROM encounters WHERE encounter_number = ?", Long.class, encounterNumber);
    }

    @Test
    void getByIdReturnsEntityGraphIncludingPatientSsn() {
        ResponseEntity<JsonNode> response = getJson("/v1/encounters/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(fieldNames(body)).containsExactlyInAnyOrder(
                "id", "encounterNumber", "patient", "attendingProvider", "encounterType", "status",
                "encounterDateTime", "admitDateTime", "dischargeDateTime", "department", "room", "bed",
                "chiefComplaint", "priority", "visitType", "diagnoses", "notes", "createdAt", "updatedAt",
                "createdBy", "version");
        assertThat(body.get("encounterNumber").asText()).isEqualTo("ENC-2024-000001");
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("encounterDateTime").asText()).isEqualTo("2024-01-15T09:00:00");
        assertThat(body.get("patient").get("mrn").asText()).isEqualTo("MRN-2019-00001");
        assertThat(body.get("patient").get("ssn").asText()).isEqualTo("123-45-6789");
        assertThat(body.get("patient").get("address").get("formattedAddress").asText())
                .isEqualTo("123 Oak Street, Apt 4B, Springfield, IL 62701");
        assertThat(body.get("attendingProvider").get("npi").asText()).isEqualTo("1234567890");
        assertThat(body.get("diagnoses").isArray()).isTrue();
    }

    @Test
    void getByUnknownIdIs404WithEmptyBody() {
        ResponseEntity<String> response = rest.getForEntity("/v1/encounters/987654321", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void endpointsReturningLazyAssociationsFailToSerialize() {
        assertThat(getJson("/v1/encounters/number/" + encounterNumber).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(getJson("/v1/encounters/patient/3").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(getJson("/v1/encounters/status/SCHEDULED").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(getJson("/v1/encounters/date-range?startDate=2031-02-03&endDate=2031-02-03").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void unknownPatientReturnsEmptyList() {
        ResponseEntity<JsonNode> response = getJson("/v1/encounters/patient/987654321");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void invalidStatusOrMissingDateParamsAre400() {
        assertThat(getJson("/v1/encounters/status/BOGUS").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getJson("/v1/encounters/date-range?startDate=2024-01-01").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getJson("/v1/encounters/provider/1/schedule?date=not-a-date").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void misalignedSeedEncounterIsA500() {
        assertThat(getJson("/v1/encounters/15").getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void createAndUpdateWithReferenceOnlyAssociationsFail() {
        String body = "{\"patient\":{\"id\":3},\"attendingProvider\":{\"id\":2},\"encounterType\":\"OFFICE_VISIT\","
                + "\"encounterDateTime\":\"2031-03-01T09:00:00\",\"chiefComplaint\":\"test\"}";

        assertThat(sendJson(HttpMethod.POST, "/v1/encounters", body).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(sendJson(HttpMethod.PUT, "/v1/encounters/" + encounterId, body).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(sendJson(HttpMethod.PUT, "/v1/encounters/987654321", body).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status()).isEqualTo("SCHEDULED");
    }

    @Test
    void lifecycleTransitionsPersistStatusAndBumpVersion() {
        assertThat(post("/check-in").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status()).isEqualTo("CHECKED_IN");

        assertThat(post("/start").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status()).isEqualTo("IN_PROGRESS");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        ResponseEntity<String> completed = rest.exchange("/v1/encounters/" + encounterId + "/complete",
                HttpMethod.POST, new HttpEntity<>("Discharged home", headers), String.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody()).isNull();

        JsonNode encounter = getJson("/v1/encounters/" + encounterId).getBody();
        assertThat(encounter.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(encounter.get("notes").asText()).isEqualTo("Discharged home");
        assertThat(encounter.get("version").asLong()).isEqualTo(3L);
    }

    @Test
    void completeWithoutBodyKeepsExistingNotes() {
        assertThat(post("/complete").getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode encounter = getJson("/v1/encounters/" + encounterId).getBody();
        assertThat(encounter.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(encounter.get("notes").asText()).isEqualTo("initial");
    }

    @Test
    void cancelAndNoShowAreUnconditional() {
        assertThat(post("/complete").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cancel").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status()).isEqualTo("CANCELLED");

        assertThat(post("/no-show").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status()).isEqualTo("NO_SHOW");
    }

    @Test
    void transitionsOnUnknownEncounterSilentlySucceed() {
        for (String action : new String[] {"/check-in", "/start", "/complete", "/cancel", "/no-show"}) {
            ResponseEntity<String> response = rest.postForEntity("/v1/encounters/987654321" + action, null, String.class);
            assertThat(response.getStatusCode()).as(action).isEqualTo(HttpStatus.OK);
        }
    }

    private ResponseEntity<String> post(String action) {
        return rest.postForEntity("/v1/encounters/" + encounterId + action, null, String.class);
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM encounters WHERE id = ?", String.class, encounterId);
    }
}
