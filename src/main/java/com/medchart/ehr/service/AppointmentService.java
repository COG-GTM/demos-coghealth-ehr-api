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
        
        // Cache the result (NOTE: This caches PII - see InsuranceCache for HIPAA issue)
        if (result.isEligible()) {
            insuranceCache.cacheEligibility(
                patientMrn, 
                result.getPatientSsn(), // HIPAA ISSUE: SSN should not be cached
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
        // HIPAA ISSUE: No TTL check - cached data lives forever
        // Should expire after 24 hours per payer requirements
        return false;
    }
}
