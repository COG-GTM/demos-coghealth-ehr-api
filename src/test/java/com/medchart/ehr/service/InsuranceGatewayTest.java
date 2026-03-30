package com.medchart.ehr.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsuranceGateway")
class InsuranceGatewayTest {

    @InjectMocks
    private InsuranceGateway insuranceGateway;

    @Nested
    @DisplayName("verifyEligibility")
    class VerifyEligibility {

        @Test
        @DisplayName("should return eligible result with mock data")
        void shouldReturnEligibleResult() {
            AppointmentService.EligibilityResult result =
                    insuranceGateway.verifyEligibility("MRN001", "PAYER1");

            assertThat(result).isNotNull();
            assertThat(result.isEligible()).isTrue();
            assertThat(result.getMemberId()).isNotNull();
            assertThat(result.getPlanName()).isEqualTo("Premium Health Plan");
            assertThat(result.getCopayRequired()).isEqualByComparingTo(new BigDecimal("25.00"));
            assertThat(result.getDeductibleRemaining()).isEqualByComparingTo(new BigDecimal("500.00"));
        }

        @Test
        @DisplayName("should generate member ID from MRN hash")
        void shouldGenerateMemberIdFromMrnHash() {
            AppointmentService.EligibilityResult result1 =
                    insuranceGateway.verifyEligibility("MRN001", "PAYER1");
            AppointmentService.EligibilityResult result2 =
                    insuranceGateway.verifyEligibility("MRN001", "PAYER1");

            assertThat(result1.getMemberId()).isEqualTo(result2.getMemberId());
            assertThat(result1.getMemberId()).startsWith("INS");
        }

        @Test
        @DisplayName("should mask SSN in response")
        void shouldMaskSsnInResponse() {
            AppointmentService.EligibilityResult result =
                    insuranceGateway.verifyEligibility("MRN001", "PAYER1");

            assertThat(result.getPatientSsn()).startsWith("XXX-XX-");
        }
    }

    @Nested
    @DisplayName("submitClaim")
    class SubmitClaim {

        @Test
        @DisplayName("should return claim reference number")
        void shouldReturnClaimReference() {
            String claimRef = insuranceGateway.submitClaim(
                    "ENC-001", "MRN001", "PAYER1",
                    new BigDecimal("150.00"), "Z00.00");

            assertThat(claimRef).isNotNull();
            assertThat(claimRef).startsWith("CLM");
        }

        @Test
        @DisplayName("should generate claim references with CLM prefix and timestamp")
        void shouldGenerateClaimReferencesWithPrefix() {
            String claimRef = insuranceGateway.submitClaim(
                    "ENC-001", "MRN001", "PAYER1",
                    new BigDecimal("150.00"), "Z00.00");

            assertThat(claimRef).startsWith("CLM");
            assertThat(claimRef.length()).isGreaterThan(3);
        }
    }

    @Nested
    @DisplayName("checkClaimStatus")
    class CheckClaimStatus {

        @Test
        @DisplayName("should return pending status for claim")
        void shouldReturnPendingStatus() {
            InsuranceGateway.ClaimStatus status =
                    insuranceGateway.checkClaimStatus("CLM12345");

            assertThat(status).isNotNull();
            assertThat(status.getClaimReference()).isEqualTo("CLM12345");
            assertThat(status.getStatus()).isEqualTo("PENDING");
            assertThat(status.getMessage()).isEqualTo("Awaiting payer review");
        }
    }
}
