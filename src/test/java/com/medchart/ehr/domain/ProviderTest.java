package com.medchart.ehr.domain;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderTest {

    @Test
    void getFullName_basicName_returnsFirstAndLast() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Anderson")
                .build();

        assertEquals("Sarah Anderson", provider.getFullName());
    }

    @Test
    void getFullName_withMiddleName_includesMiddle() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .middleName("Jane")
                .lastName("Anderson")
                .build();

        assertEquals("Sarah Jane Anderson", provider.getFullName());
    }

    @Test
    void getFullName_withSuffix_includesSuffix() {
        Provider provider = Provider.builder()
                .firstName("John")
                .lastName("Smith")
                .suffix("Jr.")
                .build();

        assertEquals("John Smith, Jr.", provider.getFullName());
    }

    @Test
    void getFullName_withCredentials_includesCredentials() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Anderson")
                .credentials("MD")
                .build();

        assertEquals("Sarah Anderson, MD", provider.getFullName());
    }

    @Test
    void getFullName_allParts_includesEverything() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .middleName("Jane")
                .lastName("Anderson")
                .suffix("III")
                .credentials("MD, FACP")
                .build();

        assertEquals("Sarah Jane Anderson, III, MD, FACP", provider.getFullName());
    }

    @Test
    void getDisplayName_withCredentials_returnsLastFirstCredentials() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Anderson")
                .credentials("MD")
                .build();

        assertEquals("Anderson, Sarah, MD", provider.getDisplayName());
    }

    @Test
    void getDisplayName_withoutCredentials_returnsLastFirst() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Anderson")
                .build();

        assertEquals("Anderson, Sarah", provider.getDisplayName());
    }

    @Test
    void builder_defaultValues_areCorrect() {
        Provider provider = Provider.builder()
                .firstName("Sarah")
                .lastName("Anderson")
                .npi("1234567890")
                .providerType(ProviderType.PHYSICIAN)
                .build();

        assertTrue(provider.getActive());
        assertTrue(provider.getAcceptingPatients());
    }
}
