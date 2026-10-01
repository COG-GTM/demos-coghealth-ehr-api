package com.medchart.ehr.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class InsuranceGatewayTest {

    private final InsuranceGateway insuranceGateway = new InsuranceGateway();

    @Test
    void verifyEligibility_returnsEligibleResult() {
        AppointmentService.EligibilityResult result = insuranceGateway.verifyEligibility("MRN001", "PAY001");

        assertTrue(result.isEligible());
        assertNotNull(result.getMemberId());
        assertEquals("Premium Health Plan", result.getPlanName());
        assertEquals(new BigDecimal("25.00"), result.getCopayRequired());
        assertEquals(new BigDecimal("500.00"), result.getDeductibleRemaining());
    }

    @Test
    void verifyEligibility_memberIdDerivedFromMrn() {
        AppointmentService.EligibilityResult result = insuranceGateway.verifyEligibility("MRN001", "PAY001");

        assertTrue(result.getMemberId().startsWith("INS"));
    }

    @Test
    void verifyEligibility_ssnPartiallyMasked() {
        AppointmentService.EligibilityResult result = insuranceGateway.verifyEligibility("MRN001", "PAY001");

        assertNotNull(result.getPatientSsn());
        assertTrue(result.getPatientSsn().startsWith("XXX-XX-"));
    }

    @Test
    void submitClaim_returnsClaimReference() {
        String claimRef = insuranceGateway.submitClaim("ENC001", "MRN001", "PAY001",
                new BigDecimal("150.00"), "J45.20");

        assertNotNull(claimRef);
        assertTrue(claimRef.startsWith("CLM"));
    }

    @Test
    void checkClaimStatus_returnsStatus() {
        InsuranceGateway.ClaimStatus status = insuranceGateway.checkClaimStatus("CLM12345");

        assertNotNull(status);
        assertEquals("CLM12345", status.getClaimReference());
        assertEquals("PENDING", status.getStatus());
        assertEquals("Awaiting payer review", status.getMessage());
    }
}
