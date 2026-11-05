package com.medchart.ehr.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsuranceGatewayTest {

    private final InsuranceGateway gateway = new InsuranceGateway();

    @Test
    @DisplayName("BR-SVC-14: eligibility responses are structured and carry only a masked SSN (last 4)")
    void brSvc14_structuredEligibilityWithMaskedSsn() {
        AppointmentService.EligibilityResult r = gateway.verifyEligibility("MRN-TEST-1234", "PAYER-A");
        assertTrue(r.isEligible());
        assertEquals("XXX-XX-1234", r.getPatientSsn());
        assertEquals("INS" + "MRN-TEST-1234".hashCode(), r.getMemberId());
        assertEquals("Premium Health Plan", r.getPlanName());
        assertEquals(new BigDecimal("25.00"), r.getCopayRequired());
        assertEquals(new BigDecimal("500.00"), r.getDeductibleRemaining());
    }

    @Test
    @DisplayName("BR-SVC-14: identifiers shorter than 4 chars are masked without failing (boundary)")
    void brSvc14_shortIdentifierMasked() {
        assertEquals("XXX-XX-12", gateway.verifyEligibility("12", "P").getPatientSsn());
    }

    @Test
    @DisplayName("BR-SVC-14: an interrupted gateway call restores the interrupt flag and still returns a response")
    void brSvc14_interruptHandled() {
        Thread.currentThread().interrupt();
        try {
            assertTrue(gateway.verifyEligibility("MRN-0001", "P").isEligible());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("BR-SVC-15: submitted claims get a CLM reference and start in PENDING status")
    void brSvc15_claimLifecycle() {
        String ref = gateway.submitClaim("ENC-1", "MRN-1", "PAYER-A", new BigDecimal("125.50"), "E11.9");
        assertTrue(ref.matches("CLM\\d+"), ref);

        InsuranceGateway.ClaimStatus status = gateway.checkClaimStatus(ref);
        assertEquals(ref, status.getClaimReference());
        assertEquals("PENDING", status.getStatus());
        assertEquals("Awaiting payer review", status.getMessage());
    }
}
