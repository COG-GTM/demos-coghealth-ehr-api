package com.medchart.ehr.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * External API integration for insurance verification.
 * 
 * PATTERN: External API Integration
 * - Use circuit breaker for resilience
 * - Log all external calls for debugging
 * - Handle timeouts gracefully
 * - Return structured response objects
 * - Record latency and outcome metrics for every payer call
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InsuranceGateway {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;

    private static final String ELIGIBILITY_TIMER = "insurance.eligibility.duration";
    private static final String CLAIM_SUBMIT_TIMER = "insurance.claim.submit";
    private static final String CLAIM_STATUS_TIMER = "insurance.claim.status";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";
    private static final String OUTCOME_INTERRUPTED = "interrupted";
    private static final String UNKNOWN_TAG = "unknown";

    private final MeterRegistry meterRegistry;

    /**
     * Verify patient eligibility with insurance payer.
     * 
     * PATTERN: External API call with retry logic
     */
    public AppointmentService.EligibilityResult verifyEligibility(String patientMrn, String payerId) {
        log.info("Calling insurance gateway for patient {} with payer {}", patientMrn, payerId);

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            // Simulate external API call
            // In production, this would call X12 270/271 eligibility service
            try {
                Thread.sleep(100); // Simulate network latency
            } catch (InterruptedException e) {
                outcome = OUTCOME_INTERRUPTED;
                log.warn("Eligibility call to payer {} interrupted", payerId);
                Thread.currentThread().interrupt();
            }

            // Mock response - in production this comes from payer
            return AppointmentService.EligibilityResult.builder()
                .eligible(true)
                .memberId("INS" + patientMrn.hashCode())
                .planName("Premium Health Plan")
                .copayRequired(new BigDecimal("25.00"))
                .deductibleRemaining(new BigDecimal("500.00"))
                .patientSsn("XXX-XX-" + patientMrn.substring(Math.max(0, patientMrn.length() - 4)))
                .build();
        } catch (RuntimeException e) {
            outcome = OUTCOME_FAILURE;
            log.error("Eligibility call to payer {} failed: {}", payerId, e.toString());
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(ELIGIBILITY_TIMER, "payer", tag(payerId), "outcome", outcome));
        }
    }

    /**
     * Submit claim to insurance payer.
     * 
     * PATTERN: Async submission with callback
     */
    public String submitClaim(String encounterId, String patientMrn, String payerId, 
                              BigDecimal amount, String diagnosisCodes) {
        log.info("Submitting claim for encounter {} to payer {}", encounterId, payerId);

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            // Generate claim reference number
            String claimRef = "CLM" + System.currentTimeMillis();

            // In production: async submission to clearinghouse
            // Would use @Async and return CompletableFuture

            log.info("Claim {} submitted successfully", claimRef);
            return claimRef;
        } catch (RuntimeException e) {
            outcome = OUTCOME_FAILURE;
            log.error("Claim submission for encounter {} to payer {} failed: {}", encounterId, payerId, e.toString());
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(CLAIM_SUBMIT_TIMER, "payer", tag(payerId), "outcome", outcome));
        }
    }

    /**
     * Check claim status with payer.
     */
    public ClaimStatus checkClaimStatus(String claimReference) {
        log.info("Checking status for claim {}", claimReference);

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            // Mock response
            return new ClaimStatus(claimReference, "PENDING", "Awaiting payer review");
        } catch (RuntimeException e) {
            outcome = OUTCOME_FAILURE;
            log.error("Claim status check for {} failed: {}", claimReference, e.toString());
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(CLAIM_STATUS_TIMER, "outcome", outcome));
        }
    }

    private static String tag(String value) {
        return (value == null || value.trim().isEmpty()) ? UNKNOWN_TAG : value;
    }

    public static class ClaimStatus {
        private final String claimReference;
        private final String status;
        private final String message;

        public ClaimStatus(String claimReference, String status, String message) {
            this.claimReference = claimReference;
            this.status = status;
            this.message = message;
        }

        public String getClaimReference() { return claimReference; }
        public String getStatus() { return status; }
        public String getMessage() { return message; }
    }
}
