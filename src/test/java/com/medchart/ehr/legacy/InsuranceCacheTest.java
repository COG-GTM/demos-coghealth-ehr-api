package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class InsuranceCacheTest {

    private InsuranceCache insuranceCache;

    @BeforeEach
    void setUp() {
        insuranceCache = new InsuranceCache();
    }

    @Test
    void cacheEligibility_storesEntryRetrievableByGetEligibility() {
        insuranceCache.cacheEligibility("MRN001", "123-45-6789", "PAYER01",
                "MEM001", true, "Gold Plan", "$20", "$500");

        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("MRN001", "PAYER01");

        assertThat(result).isNotNull();
        assertThat(result.patientMrn).isEqualTo("MRN001");
        assertThat(result.patientSsn).isEqualTo("123-45-6789");
        assertThat(result.payerId).isEqualTo("PAYER01");
        assertThat(result.memberId).isEqualTo("MEM001");
        assertThat(result.eligible).isTrue();
        assertThat(result.planName).isEqualTo("Gold Plan");
        assertThat(result.copay).isEqualTo("$20");
        assertThat(result.deductible).isEqualTo("$500");
    }

    @Test
    void getEligibility_returnsNull_forUnknownPatientPayerCombination() {
        InsuranceCache.CachedEligibility result = insuranceCache.getEligibility("UNKNOWN", "PAYER01");
        assertThat(result).isNull();
    }

    @Test
    void clearPatientCache_removesEntriesForThatPatientOnly() {
        insuranceCache.cacheEligibility("MRN001", "111-11-1111", "PAYER01",
                "MEM001", true, "Gold Plan", "$20", "$500");
        insuranceCache.cacheEligibility("MRN001", "111-11-1111", "PAYER02",
                "MEM002", true, "Silver Plan", "$30", "$1000");
        insuranceCache.cacheEligibility("MRN002", "222-22-2222", "PAYER01",
                "MEM003", true, "Bronze Plan", "$40", "$2000");

        assertThat(insuranceCache.getCacheSize()).isEqualTo(3);

        insuranceCache.clearPatientCache("MRN001");

        assertThat(insuranceCache.getEligibility("MRN001", "PAYER01")).isNull();
        assertThat(insuranceCache.getEligibility("MRN001", "PAYER02")).isNull();
        assertThat(insuranceCache.getEligibility("MRN002", "PAYER01")).isNotNull();
        assertThat(insuranceCache.getCacheSize()).isEqualTo(1);
    }

    @Test
    void clearAllCache_emptiesCacheCompletely() {
        insuranceCache.cacheEligibility("MRN001", "111-11-1111", "PAYER01",
                "MEM001", true, "Gold Plan", "$20", "$500");
        insuranceCache.cacheEligibility("MRN002", "222-22-2222", "PAYER01",
                "MEM002", true, "Silver Plan", "$30", "$1000");

        assertThat(insuranceCache.getCacheSize()).isEqualTo(2);

        insuranceCache.clearAllCache();

        assertThat(insuranceCache.getCacheSize()).isEqualTo(0);
        assertThat(insuranceCache.getEligibility("MRN001", "PAYER01")).isNull();
        assertThat(insuranceCache.getEligibility("MRN002", "PAYER01")).isNull();
    }

    @Test
    void getCacheSize_returnsAccurateCount() {
        assertThat(insuranceCache.getCacheSize()).isEqualTo(0);

        insuranceCache.cacheEligibility("MRN001", "111-11-1111", "PAYER01",
                "MEM001", true, "Gold Plan", "$20", "$500");
        assertThat(insuranceCache.getCacheSize()).isEqualTo(1);

        insuranceCache.cacheEligibility("MRN002", "222-22-2222", "PAYER01",
                "MEM002", true, "Silver Plan", "$30", "$1000");
        assertThat(insuranceCache.getCacheSize()).isEqualTo(2);

        insuranceCache.clearPatientCache("MRN001");
        assertThat(insuranceCache.getCacheSize()).isEqualTo(1);

        insuranceCache.clearAllCache();
        assertThat(insuranceCache.getCacheSize()).isEqualTo(0);
    }

    @Test
    void cacheEligibility_storesSsnWithNoTtl_hipaaGap() {
        insuranceCache.cacheEligibility("MRN001", "123-45-6789", "PAYER01",
                "MEM001", true, "Gold Plan", "$20", "$500");

        InsuranceCache.CachedEligibility cached = insuranceCache.getEligibility("MRN001", "PAYER01");

        // HIPAA gap: SSN is stored in plaintext in the cache
        assertThat(cached.patientSsn).isEqualTo("123-45-6789");

        // cachedAt is set
        assertThat(cached.cachedAt).isNotNull();

        // No expiration/TTL field exists on CachedEligibility — documenting the HIPAA issue
        // The CachedEligibility class has no expiration, expiresAt, or ttl field.
        // This means cached PHI (including SSN) lives indefinitely until manually cleared.
        java.lang.reflect.Field[] fields = InsuranceCache.CachedEligibility.class.getDeclaredFields();
        boolean hasExpirationField = false;
        for (java.lang.reflect.Field field : fields) {
            String name = field.getName().toLowerCase();
            if (name.contains("expir") || name.contains("ttl") || name.contains("timeout")) {
                hasExpirationField = true;
                break;
            }
        }
        assertThat(hasExpirationField)
                .as("CachedEligibility should NOT have an expiration field (documenting HIPAA gap)")
                .isFalse();
    }
}
