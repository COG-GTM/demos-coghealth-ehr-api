package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InsuranceCacheTest {

    private InsuranceCache insuranceCache;

    @BeforeEach
    void setUp() {
        insuranceCache = new InsuranceCache();
    }

    @Test
    void cacheEligibility_storesEntry() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Premium Plan", "25.00", "500.00");

        assertEquals(1, insuranceCache.getCacheSize());
    }

    @Test
    void getEligibility_existingEntry_returnsCache() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Premium Plan", "25.00", "500.00");

        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN001", "PAY001");

        assertNotNull(result);
        assertEquals("MRN001", result.patientMrn);
        assertEquals("PAY001", result.payerId);
        assertEquals("MEM001", result.memberId);
        assertTrue(result.eligible);
        assertEquals("Premium Plan", result.planName);
        assertEquals("25.00", result.copay);
        assertEquals("500.00", result.deductible);
        assertNotNull(result.cachedAt);
    }

    @Test
    void getEligibility_nonExistingEntry_returnsNull() {
        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN999", "PAY001");

        assertNull(result);
    }

    @Test
    void getEligibility_wrongPayer_returnsNull() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Premium Plan", "25.00", "500.00");

        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN001", "PAY999");

        assertNull(result);
    }

    @Test
    void cacheEligibility_sameKey_overwritesEntry() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Plan A", "25.00", "500.00");
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM002", false, "Plan B", "50.00", "1000.00");

        assertEquals(1, insuranceCache.getCacheSize());
        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN001", "PAY001");
        assertEquals("MEM002", result.memberId);
        assertFalse(result.eligible);
    }

    @Test
    void clearPatientCache_removesAllEntriesForPatient() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Plan A", "25.00", "500.00");
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY002",
                "MEM002", true, "Plan B", "30.00", "600.00");
        insuranceCache.cacheEligibility("MRN002", "SSN456", "PAY001",
                "MEM003", true, "Plan C", "20.00", "400.00");

        assertEquals(3, insuranceCache.getCacheSize());

        insuranceCache.clearPatientCache("MRN001");

        assertEquals(1, insuranceCache.getCacheSize());
        assertNull(insuranceCache.getEligibility("MRN001", "PAY001"));
        assertNull(insuranceCache.getEligibility("MRN001", "PAY002"));
        assertNotNull(insuranceCache.getEligibility("MRN002", "PAY001"));
    }

    @Test
    void clearAllCache_removesEverything() {
        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Plan A", "25.00", "500.00");
        insuranceCache.cacheEligibility("MRN002", "SSN456", "PAY002",
                "MEM002", true, "Plan B", "30.00", "600.00");

        insuranceCache.clearAllCache();

        assertEquals(0, insuranceCache.getCacheSize());
    }

    @Test
    void getCacheSize_returnsCorrectCount() {
        assertEquals(0, insuranceCache.getCacheSize());

        insuranceCache.cacheEligibility("MRN001", "SSN123", "PAY001",
                "MEM001", true, "Plan A", "25.00", "500.00");
        assertEquals(1, insuranceCache.getCacheSize());

        insuranceCache.cacheEligibility("MRN002", "SSN456", "PAY002",
                "MEM002", true, "Plan B", "30.00", "600.00");
        assertEquals(2, insuranceCache.getCacheSize());
    }

    @Test
    void cacheEligibility_storesSsn() {
        insuranceCache.cacheEligibility("MRN001", "123-45-6789", "PAY001",
                "MEM001", true, "Plan A", "25.00", "500.00");

        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN001", "PAY001");
        assertEquals("123-45-6789", result.patientSsn);
    }
}
