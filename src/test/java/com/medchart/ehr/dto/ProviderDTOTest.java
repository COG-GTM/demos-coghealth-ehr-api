package com.medchart.ehr.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProviderDTOTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Provider stored() {
        return Provider.builder()
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Chen")
                .providerType(ProviderType.PHYSICIAN)
                .email("old@example.org")
                .pager("555-0100")
                .terminationDate(LocalDate.of(2026, 1, 1))
                .department("Surgery")
                .build();
    }

    @Test
    void explicitNullClearsOptionalFields() throws Exception {
        Provider provider = stored();
        mapper.readValue("{\"email\":null,\"pager\":null,\"terminationDate\":null}", ProviderDTO.class)
                .applyUpdateTo(provider);

        assertNull(provider.getEmail());
        assertNull(provider.getPager());
        assertNull(provider.getTerminationDate());
        assertEquals("Surgery", provider.getDepartment());
    }

    @Test
    void omittedFieldsAreKept() throws Exception {
        Provider provider = stored();
        mapper.readValue("{\"department\":\"Cardiology\"}", ProviderDTO.class).applyUpdateTo(provider);

        assertEquals("Cardiology", provider.getDepartment());
        assertEquals("old@example.org", provider.getEmail());
        assertEquals("555-0100", provider.getPager());
    }

    @Test
    void explicitNullDoesNotClearRequiredFieldsOrNpi() throws Exception {
        Provider provider = stored();
        mapper.readValue("{\"firstName\":null,\"lastName\":null,\"providerType\":null,\"npi\":\"9999999999\"}",
                ProviderDTO.class).applyUpdateTo(provider);

        assertEquals("Alice", provider.getFirstName());
        assertEquals("Chen", provider.getLastName());
        assertEquals(ProviderType.PHYSICIAN, provider.getProviderType());
        assertEquals("1234567890", provider.getNpi());
    }
}
