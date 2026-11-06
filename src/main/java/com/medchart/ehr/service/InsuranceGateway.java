package com.medchart.ehr.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.function.Supplier;

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
public class InsuranceGateway {

    private static final int TIMEOUT_MS = 5000;
    private static final int MAX_RETRIES = 3;

    private static final String ELIGIBILITY_TIMER = "insurance.gateway.eligibility.duration";
    private static final String CLAIM_SUBMIT_TIMER = "insurance.gateway.claim.submit.duration";
    private static final String CLAIM_STATUS_TIMER = "insurance.gateway.claim.status.duration";
    private static final String FAILURE_COUNTER = "insurance.gateway.failures";

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";
    private static final String UNKNOWN_PAYER = "unknown";

    private final MeterRegistry meterRegistry;

    public InsuranceGateway(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Verify patient eligibility with insurance payer.
     * 
     * PATTERN: External API call with retry logic
     */
    public AppointmentService.EligibilityResult verifyEligibility(String patientMrn, String payerId) {
        log.info("Calling insurance gateway for payer {}", payerId);

        return call(ELIGIBILITY_TIMER, "eligibility", payerId, () -> {
            // Simulate external API call
            // In production, this would call X12 270/271 eligibility service
            try {
                Thread.sleep(100); // Simulate network latency
            } catch (InterruptedException e) {
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
        });
    }

    /**
     * Submit claim to insurance payer.
     * 
     * PATTERN: Async submission with callback
     */
    public String submitClaim(String encounterId, String patientMrn, String payerId, 
                              BigDecimal amount, String diagnosisCodes) {
        log.info("Submitting claim for encounter {} to payer {}", encounterId, payerId);

        String claimRef = call(CLAIM_SUBMIT_TIMER, "claim.submit", payerId, () -> {
            // Generate claim reference number
            // In production: async submission to clearinghouse
            // Would use @Async and return CompletableFuture
            return "CLM" + System.currentTimeMillis();
        });

        log.info("Claim {} submitted successfully", claimRef);
        return claimRef;
    }

    /**
     * Check claim status with payer.
     */
    public ClaimStatus checkClaimStatus(String claimReference) {
        log.info("Checking status for claim {}", claimReference);

        return call(CLAIM_STATUS_TIMER, "claim.status", null,
            () -> new ClaimStatus(claimReference, "PENDING", "Awaiting payer review"));
    }

    /**
     * Execute an external payer call, recording its latency and outcome.
     * Tags stay low cardinality: operation, payer and outcome only - never patient identifiers.
     */
    private <T> T call(String timerName, String operation, String payerId, Supplier<T> payerCall) {
        Tags tags = Tags.of("operation", operation, "payer", payerId != null ? payerId : UNKNOWN_PAYER);
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = OUTCOME_SUCCESS;
        try {
            return payerCall.get();
        } catch (RuntimeException e) {
            outcome = OUTCOME_FAILURE;
            meterRegistry.counter(FAILURE_COUNTER, tags.and("exception", e.getClass().getSimpleName())).increment();
            log.error("Insurance gateway {} call failed for payer {}", operation, payerId, e);
            throw e;
        } finally {
            sample.stop(meterRegistry.timer(timerName, tags.and("outcome", outcome)));
        }
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
