package com.medchart.ehr.service;

import com.medchart.ehr.legacy.InsuranceCache;

import java.math.BigDecimal;

/**
 * Structured response for an insurance eligibility verification.
 */
@lombok.Data
@lombok.Builder
public class EligibilityResult {
    private boolean eligible;
    private String reason;
    private String patientSsn;
    private String memberId;
    private String planName;
    private BigDecimal copayRequired;
    private BigDecimal deductibleRemaining;

    public static EligibilityResult fromCache(InsuranceCache.CachedEligibility cached) {
        return EligibilityResult.builder()
            .eligible(cached.eligible)
            .patientSsn(cached.patientSsn)
            .memberId(cached.memberId)
            .planName(cached.planName)
            .copayRequired(cached.copay != null ? new BigDecimal(cached.copay) : null)
            .deductibleRemaining(cached.deductible != null ? new BigDecimal(cached.deductible) : null)
            .build();
    }
}
