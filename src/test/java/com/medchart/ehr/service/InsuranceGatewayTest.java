package com.medchart.ehr.service;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsuranceGatewayTest {

    private SimpleMeterRegistry registry;
    private InsuranceGateway gateway;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gateway = new InsuranceGateway(registry);
    }

    @Test
    void verifyEligibilityRecordsSuccessTimer() {
        AppointmentService.EligibilityResult result = gateway.verifyEligibility("MRN12345", "PAYER1");

        assertNotNull(result);
        Timer timer = registry.find("insurance.gateway.eligibility.duration")
            .tag("operation", "eligibility")
            .tag("payer", "PAYER1")
            .tag("outcome", "success")
            .timer();
        assertNotNull(timer);
        assertEquals(1, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void verifyEligibilityFailureIncrementsFailureCounterAndRethrows() {
        assertThrows(RuntimeException.class, () -> gateway.verifyEligibility(null, "PAYER1"));

        assertEquals(1.0, registry.find("insurance.gateway.failures")
            .tag("operation", "eligibility")
            .tag("payer", "PAYER1")
            .counter()
            .count());
        assertEquals(1, registry.find("insurance.gateway.eligibility.duration")
            .tag("outcome", "failure")
            .timer()
            .count());
    }

    @Test
    void submitClaimRecordsTimer() {
        String claimRef = gateway.submitClaim("ENC1", "MRN12345", "PAYER2", new BigDecimal("120.00"), "E11.9");

        assertTrue(claimRef.startsWith("CLM"));
        assertEquals(1, registry.find("insurance.gateway.claim.submit.duration")
            .tag("payer", "PAYER2")
            .tag("outcome", "success")
            .timer()
            .count());
    }

    @Test
    void checkClaimStatusRecordsTimer() {
        InsuranceGateway.ClaimStatus status = gateway.checkClaimStatus("CLM1");

        assertEquals("PENDING", status.getStatus());
        assertEquals(1, registry.find("insurance.gateway.claim.status.duration")
            .tag("operation", "claim.status")
            .tag("outcome", "success")
            .timer()
            .count());
    }
}
