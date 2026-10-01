package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.legacy.InsuranceCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Appointment scheduling service with insurance eligibility verification.
 * 
 * PATTERN: Insurance Eligibility Check
 * Before scheduling, always verify patient insurance eligibility.
 * This pattern should be followed for any service that creates billable encounters.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AppointmentService {

    private final InsuranceCache insuranceCache;
    private final InsuranceGateway insuranceGateway;

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Appointment", description = "Schedule appointment")
    public Map<String, Object> scheduleAppointment(Long patientId, String patientMrn, 
                                                    String payerId, LocalDate appointmentDate,
                                                    String appointmentType, Long providerId) {
        
        // Step 1: Check eligibility (PATTERN: Always verify before scheduling)
        EligibilityResult eligibility = checkInsuranceEligibility(patientMrn, payerId);
        
        if (!eligibility.isEligible()) {
            log.warn("Patient {} not eligible for insurance {}", patientMrn, payerId);
            throw new IllegalStateException("Patient insurance not eligible: " + eligibility.getReason());
        }
        
        // Step 2: Verify copay requirements
        if (eligibility.getCopayRequired() != null && eligibility.getCopayRequired().compareTo(java.math.BigDecimal.ZERO) > 0) {
            log.info("Copay of {} required for patient {}", eligibility.getCopayRequired(), patientMrn);
        }
        
        // Step 3: Create appointment record
        Map<String, Object> appointment = new HashMap<>();
        appointment.put("patientId", patientId);
        appointment.put("patientMrn", patientMrn);
        appointment.put("appointmentDate", appointmentDate);
        appointment.put("appointmentType", appointmentType);
        appointment.put("providerId", providerId);
        appointment.put("eligibilityVerified", true);
        appointment.put("copayAmount", eligibility.getCopayRequired());
        appointment.put("status", "SCHEDULED");
        
        log.info("Scheduled appointment for patient {} on {}", patientMrn, appointmentDate);
        return appointment;
    }

    /**
     * PATTERN: Insurance Eligibility Check
     * 
     * 1. Check cache first (performance optimization)
     * 2. If cache miss or stale, call external gateway
     * 3. Cache the result for future lookups
     * 4. Return structured eligibility result
     */
    public EligibilityResult checkInsuranceEligibility(String patientMrn, String payerId) {
        // Check cache first
        InsuranceCache.CachedEligibility cached = insuranceCache.getEligibility(patientMrn, payerId);
        
        if (cached != null && !isStale(cached)) {
            log.debug("Using cached eligibility for patient {}", patientMrn);
            return EligibilityResult.fromCache(cached);
        }
        
        // Cache miss - call external gateway
        log.info("Checking eligibility with payer {} for patient {}", payerId, patientMrn);
        EligibilityResult result = insuranceGateway.verifyEligibility(patientMrn, payerId);
        
        if (result.isEligible()) {
            insuranceCache.cacheEligibility(
                patientMrn, 
                payerId,
                result.getMemberId(),
                result.isEligible(),
                result.getPlanName(),
                result.getCopayRequired() != null ? result.getCopayRequired().toString() : null,
                result.getDeductibleRemaining() != null ? result.getDeductibleRemaining().toString() : null
            );
        }
        
        return result;
    }

    private boolean isStale(InsuranceCache.CachedEligibility cached) {
        if (cached.cachedAt == null) {
            return true;
        }
        return java.time.Duration.between(cached.cachedAt, java.time.LocalDateTime.now())
            .compareTo(java.time.Duration.ofHours(24)) > 0;
    }

    @lombok.Data
    @lombok.Builder
    public static class EligibilityResult {
        private boolean eligible;
        private String reason;
        private String memberId;
        private String planName;
        private java.math.BigDecimal copayRequired;
        private java.math.BigDecimal deductibleRemaining;

        public static EligibilityResult fromCache(InsuranceCache.CachedEligibility cached) {
            return EligibilityResult.builder()
                .eligible(cached.eligible)
                .memberId(cached.memberId)
                .planName(cached.planName)
                .copayRequired(cached.copay != null ? new java.math.BigDecimal(cached.copay) : null)
                .deductibleRemaining(cached.deductible != null ? new java.math.BigDecimal(cached.deductible) : null)
                .build();
        }
    }
}
