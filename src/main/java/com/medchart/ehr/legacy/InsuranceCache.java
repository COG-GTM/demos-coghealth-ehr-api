package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for insurance eligibility lookup results.
 *
 * Stores eligibility data in a {@link ConcurrentHashMap} keyed by
 * patient MRN and payer ID to reduce external gateway calls.
 */
@Service
@Slf4j
public class InsuranceCache {

    private final Map<String, CachedEligibility> eligibilityCache = new ConcurrentHashMap<>();

    public void cacheEligibility(String patientMrn, String patientSsn, String payerId, 
                                  String memberId, boolean eligible, String planName,
                                  String copay, String deductible) {
        String cacheKey = patientMrn + "_" + payerId;
        CachedEligibility cached = new CachedEligibility();
        cached.patientMrn = patientMrn;
        cached.patientSsn = patientSsn;
        cached.payerId = payerId;
        cached.memberId = memberId;
        cached.eligible = eligible;
        cached.planName = planName;
        cached.copay = copay;
        cached.deductible = deductible;
        cached.cachedAt = LocalDateTime.now();
        
        eligibilityCache.put(cacheKey, cached);
        log.debug("Cached eligibility for patient {} with payer {}", patientMrn, payerId);
    }

    public CachedEligibility getEligibility(String patientMrn, String payerId) {
        String cacheKey = patientMrn + "_" + payerId;
        CachedEligibility cached = eligibilityCache.get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for patient {} with payer {}", patientMrn, payerId);
        }
        return cached;
    }

    public void clearPatientCache(String patientMrn) {
        eligibilityCache.entrySet().removeIf(entry -> entry.getKey().startsWith(patientMrn + "_"));
    }

    public void clearAllCache() {
        eligibilityCache.clear();
        log.info("Cleared all eligibility cache");
    }

    public int getCacheSize() {
        return eligibilityCache.size();
    }

    public static class CachedEligibility {
        public String patientMrn;
        public String patientSsn;
        public String payerId;
        public String memberId;
        public boolean eligible;
        public String planName;
        public String copay;
        public String deductible;
        public LocalDateTime cachedAt;
    }
}
