package com.medchart.ehr.domain;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderTest {

    @Test
    void getFullName_shouldReturnBasicName() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .build();

        assertEquals("Sarah Johnson", provider.getFullName());
    }

    @Test
    void getFullName_shouldIncludeMiddleName() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .middleName("Ann")
                .lastName("Johnson")
                .build();

        assertEquals("Sarah Ann Johnson", provider.getFullName());
    }

    @Test
    void getFullName_shouldIncludeSuffix() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .suffix("III")
                .build();

        assertEquals("Sarah Johnson, III", provider.getFullName());
    }

    @Test
    void getFullName_shouldIncludeCredentials() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .credentials("MD")
                .build();

        assertEquals("Sarah Johnson, MD", provider.getFullName());
    }

    @Test
    void getFullName_shouldIncludeAllParts() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .middleName("Ann")
                .lastName("Johnson")
                .suffix("Jr")
                .credentials("MD, FACP")
                .build();

        assertEquals("Sarah Ann Johnson, Jr, MD, FACP", provider.getFullName());
    }

    @Test
    void getDisplayName_shouldReturnLastFirst() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .build();

        assertEquals("Johnson, Sarah", provider.getDisplayName());
    }

    @Test
    void getDisplayName_shouldIncludeCredentials() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .credentials("MD")
                .build();

        assertEquals("Johnson, Sarah, MD", provider.getDisplayName());
    }

    @Test
    void builder_shouldSetDefaults() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Johnson")
                .npi("1234567890")
                .providerType(ProviderType.PHYSICIAN)
                .build();

        assertTrue(provider.getActive());
        assertTrue(provider.getAcceptingPatients());
        assertNotNull(provider.getLicenses());
    }

    @Test
    void builder_shouldSetAllFields() {
        Provider provider = Provider.builder()
                .id(1L)
                .npi("1234567890")
                .firstName("Sarah")
                .lastName("Johnson")
                .credentials("MD")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Internal Medicine")
                .department("Medicine")
                .email("sarah.johnson@hospital.org")
                .active(true)
                .acceptingPatients(true)
                .build();

        assertEquals(1L, provider.getId());
        assertEquals("1234567890", provider.getNpi());
        assertEquals("Internal Medicine", provider.getSpecialty());
        assertEquals("Medicine", provider.getDepartment());
    }
}
