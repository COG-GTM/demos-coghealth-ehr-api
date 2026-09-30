package com.medchart.ehr.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.medchart.ehr.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PatientApiTest extends AbstractApiIntegrationTest {

    private static final List<String> PATIENT_DTO_FIELDS = List.of(
            "id", "mrn", "firstName", "middleName", "lastName", "dateOfBirth", "gender", "maritalStatus",
            "email", "phoneHome", "phoneMobile", "phoneWork", "address", "mailingAddress",
            "preferredLanguage", "ethnicity", "race", "religion", "identifiers", "emergencyContacts",
            "active", "deceased", "deceasedDate", "createdAt", "updatedAt", "fullName", "age");

    @Test
    void getByIdReturnsPatientDto() {
        ResponseEntity<JsonNode> response = getJson("/v1/patients/1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(PATIENT_DTO_FIELDS);
        assertThat(body.get("id").asLong()).isEqualTo(1L);
        assertThat(body.get("mrn").asText()).isEqualTo("MRN-2019-00001");
        assertThat(body.get("dateOfBirth").asText()).isEqualTo("1965-03-15");
        assertThat(body.get("gender").asText()).isEqualTo("MALE");
        assertThat(body.get("fullName").asText()).isEqualTo("John Robert Smith");
        assertThat(body.get("age").isInt()).isTrue();
        assertThat(body.get("createdAt").asText()).isEqualTo("2019-03-20T10:00:00");
        assertThat(fieldNames(body.get("address")))
                .containsExactlyInAnyOrder("street1", "street2", "city", "state", "zipCode", "country");
        assertThat(body.get("address").get("zipCode").asText()).isEqualTo("62701");
        assertThat(body.get("identifiers").isArray()).isTrue();
    }

    @Test
    void getByUnknownIdIsA500WithBootErrorBody() {
        ResponseEntity<JsonNode> response = getJson("/v1/patients/987654321");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertBootErrorBody(response.getBody(), 500, "Internal Server Error", "/api/v1/patients/987654321");
    }

    @Test
    void getByNonNumericIdIsA400() {
        assertThat(getJson("/v1/patients/abc").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getByMrn() {
        ResponseEntity<JsonNode> found = getJson("/v1/patients/mrn/MRN-2019-00003");
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().get("lastName").asText()).isEqualTo("Johnson");

        assertThat(getJson("/v1/patients/mrn/NOPE").getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void searchReturnsSpringDataPageJson() {
        ResponseEntity<JsonNode> response = getJson("/v1/patients/search?q=SMITH&size=50");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode page = response.getBody();
        assertThat(fieldNames(page)).contains(
                "content", "pageable", "totalElements", "totalPages", "last", "first", "size", "number",
                "numberOfElements", "sort", "empty");
        assertThat(page.get("size").asInt()).isEqualTo(50);
        assertThat(page.get("number").asInt()).isZero();
        assertThat(page.get("content").findValuesAsText("mrn")).contains("MRN-2019-00001");
        assertThat(fieldNames(page.get("content").get(0))).containsExactlyInAnyOrderElementsOf(PATIENT_DTO_FIELDS);
    }

    @Test
    void searchHonoursPageSize() {
        JsonNode page = getJson("/v1/patients/search?q=MRN-20&size=2&page=1").getBody();

        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("number").asInt()).isEqualTo(1);
        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(15);
    }

    @Test
    void searchWithoutQueryIsA400() {
        assertThat(getJson("/v1/patients/search").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGeneratesMrnAndReturns201() {
        ResponseEntity<JsonNode> created = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"Ada\",\"lastName\":\"Created\",\"dateOfBirth\":\"1990-01-02\","
                        + "\"gender\":\"FEMALE\",\"active\":true,\"deceased\":false,"
                        + "\"address\":{\"street1\":\"1 Main St\",\"city\":\"Springfield\",\"state\":\"IL\",\"zipCode\":\"62701\"}}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNull();
        JsonNode body = created.getBody();
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("mrn").asText()).matches("MRN\\d{13}");
        assertThat(body.get("fullName").asText()).isEqualTo("Ada Created");
        assertThat(body.get("createdAt").isNull()).isFalse();
        assertThat(body.get("identifiers").isNull()).isTrue();

        JsonNode fetched = getJson("/v1/patients/" + body.get("id").asLong()).getBody();
        assertThat(fetched.get("mrn").asText()).isEqualTo(body.get("mrn").asText());
        assertThat(fetched.get("address").get("street1").asText()).isEqualTo("1 Main St");
        assertThat(fetched.get("identifiers").isArray()).isTrue();
    }

    @Test
    void createKeepsClientSuppliedMrnAndRejectsDuplicates() {
        String mrn = unique("MRN-T-");
        String body = "{\"mrn\":\"" + mrn + "\",\"firstName\":\"Dup\",\"lastName\":\"Check\","
                + "\"dateOfBirth\":\"1980-05-05\",\"active\":true,\"deceased\":false}";

        ResponseEntity<JsonNode> first = sendJson(HttpMethod.POST, "/v1/patients", body);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().get("mrn").asText()).isEqualTo(mrn);

        ResponseEntity<JsonNode> duplicate = sendJson(HttpMethod.POST, "/v1/patients", body);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void createValidationErrorsAre400WithoutFieldDetails() {
        ResponseEntity<JsonNode> missingNames = sendJson(HttpMethod.POST, "/v1/patients", "{}");
        assertThat(missingNames.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertBootErrorBody(missingNames.getBody(), 400, "Bad Request", "/api/v1/patients");

        ResponseEntity<JsonNode> futureDob = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"F\",\"lastName\":\"D\",\"dateOfBirth\":\"2999-01-01\"}");
        assertThat(futureDob.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> blankFirstName = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"  \",\"lastName\":\"D\",\"dateOfBirth\":\"1990-01-01\"}");
        assertThat(blankFirstName.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> malformed = sendJson(HttpMethod.POST, "/v1/patients", "not json");
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> badEnum = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"F\",\"lastName\":\"D\",\"dateOfBirth\":\"1990-01-01\",\"gender\":\"NOT_A_GENDER\"}");
        assertThat(badEnum.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createFailsWith500WhenDatabaseNotNullColumnsAreMissing() {
        ResponseEntity<JsonNode> noDob = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"No\",\"lastName\":\"Dob\",\"active\":true,\"deceased\":false}");
        assertThat(noDob.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<JsonNode> noActiveFlag = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"No\",\"lastName\":\"Flag\",\"dateOfBirth\":\"1990-01-01\"}");
        assertThat(noActiveFlag.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateIsPartialMergeThatIgnoresNullFields() {
        JsonNode created = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"Up\",\"middleName\":\"Mid\",\"lastName\":\"Date\",\"dateOfBirth\":\"1975-07-07\","
                        + "\"email\":\"up@example.com\",\"active\":true,\"deceased\":false}").getBody();
        long id = created.get("id").asLong();

        ResponseEntity<JsonNode> updated = sendJson(HttpMethod.PUT, "/v1/patients/" + id,
                "{\"id\":999,\"mrn\":\"" + created.get("mrn").asText() + "\",\"firstName\":\"Upd\",\"lastName\":\"Ated\"}");

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = updated.getBody();
        assertThat(body.get("id").asLong()).isEqualTo(id);
        assertThat(body.get("firstName").asText()).isEqualTo("Upd");
        assertThat(body.get("lastName").asText()).isEqualTo("Ated");
        assertThat(body.get("middleName").asText()).isEqualTo("Mid");
        assertThat(body.get("email").asText()).isEqualTo("up@example.com");
        assertThat(body.get("dateOfBirth").asText()).isEqualTo("1975-07-07");

        Map<String, Object> row = jdbc.queryForMap("SELECT first_name, version FROM patients WHERE id = ?", id);
        assertThat(row.get("first_name")).isEqualTo("Upd");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    void updateErrors() {
        assertThat(sendJson(HttpMethod.PUT, "/v1/patients/987654321", "{\"firstName\":\"X\",\"lastName\":\"Y\"}")
                .getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(sendJson(HttpMethod.PUT, "/v1/patients/1", "{\"firstName\":\"\",\"lastName\":\"Y\"}")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unsupportedMethodIs405() {
        assertThat(sendJson(HttpMethod.DELETE, "/v1/patients/1", null).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void patientAccessIsAuditedAsynchronouslyAsSystemUser() throws InterruptedException {
        JsonNode created = sendJson(HttpMethod.POST, "/v1/patients",
                "{\"firstName\":\"Audit\",\"lastName\":\"Trail\",\"dateOfBirth\":\"1970-01-01\",\"active\":true,\"deceased\":false}")
                .getBody();
        long id = created.get("id").asLong();
        getJson("/v1/patients/" + id);

        Map<String, Object> event = awaitAuditEvent(id, "READ");
        assertThat(event.get("user_id")).isEqualTo("system");
        assertThat(event.get("user_name")).isEqualTo("System User");
        assertThat(event.get("resource_type")).isEqualTo("Patient");
        assertThat(event.get("description")).isEqualTo("View patient record");
        assertThat(event.get("success")).isEqualTo(true);
        assertThat(event.get("ip_address")).isNotNull();
    }

    @Test
    void failedPatientReadIsAuditedWithError() throws InterruptedException {
        long missingId = 876543210L;
        getJson("/v1/patients/" + missingId);

        Map<String, Object> event = awaitAuditEvent(missingId, "READ");
        assertThat(event.get("success")).isEqualTo(false);
        assertThat(event.get("error_message")).isEqualTo("Patient not found with id: " + missingId);
    }

    private Map<String, Object> awaitAuditEvent(long patientId, String action) throws InterruptedException {
        String sql = "SELECT * FROM audit_events WHERE patient_id = ? AND action = ? ORDER BY id DESC LIMIT 1";
        for (int attempt = 0; attempt < 50; attempt++) {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, patientId, action);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No " + action + " audit event for patient " + patientId);
    }

    static void assertBootErrorBody(JsonNode body, int status, String error, String path) {
        assertThat(fieldNames(body)).containsExactlyInAnyOrder("timestamp", "status", "error", "path");
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("error").asText()).isEqualTo(error);
        assertThat(body.get("path").asText()).isEqualTo(path);
    }
}
