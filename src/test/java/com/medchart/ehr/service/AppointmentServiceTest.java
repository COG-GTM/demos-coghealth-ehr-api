package com.medchart.ehr.service;

import com.medchart.ehr.legacy.InsuranceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppointmentService")
class AppointmentServiceTest {

    @Mock
    private InsuranceCache insuranceCache;

    @Mock
    private InsuranceGateway insuranceGateway;

    @InjectMocks
    private AppointmentService appointmentService;

    @Nested
    @DisplayName("scheduleAppointment")
    class ScheduleAppointment {

        @Test
        @DisplayName("should schedule appointment when patient is eligible")
        void shouldScheduleWhenEligible() {
            AppointmentService.EligibilityResult eligibility = AppointmentService.EligibilityResult.builder()
                    .eligible(true)
                    .memberId("INS123")
                    .planName("Premium Plan")
                    .copayRequired(new BigDecimal("25.00"))
                    .deductibleRemaining(new BigDecimal("500.00"))
                    .build();

            when(insuranceCache.getEligibility("MRN001", "PAYER1")).thenReturn(null);
            when(insuranceGateway.verifyEligibility("MRN001", "PAYER1")).thenReturn(eligibility);

            Map<String, Object> result = appointmentService.scheduleAppointment(
                    1L, "MRN001", "PAYER1", LocalDate.of(2026, 4, 1), "FOLLOW_UP", 10L);

            assertThat(result).containsEntry("patientId", 1L);
            assertThat(result).containsEntry("patientMrn", "MRN001");
            assertThat(result).containsEntry("status", "SCHEDULED");
            assertThat(result).containsEntry("eligibilityVerified", true);
            assertThat(result).containsEntry("copayAmount", new BigDecimal("25.00"));
        }

        @Test
        @DisplayName("should throw when patient is not eligible")
        void shouldThrowWhenNotEligible() {
            AppointmentService.EligibilityResult eligibility = AppointmentService.EligibilityResult.builder()
                    .eligible(false)
                    .reason("Coverage terminated")
                    .build();

            when(insuranceCache.getEligibility("MRN002", "PAYER1")).thenReturn(null);
            when(insuranceGateway.verifyEligibility("MRN002", "PAYER1")).thenReturn(eligibility);

            assertThatThrownBy(() -> appointmentService.scheduleAppointment(
                    2L, "MRN002", "PAYER1", LocalDate.of(2026, 4, 1), "FOLLOW_UP", 10L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Patient insurance not eligible");
        }

        @Test
        @DisplayName("should schedule appointment with zero copay")
        void shouldScheduleWithZeroCopay() {
            AppointmentService.EligibilityResult eligibility = AppointmentService.EligibilityResult.builder()
                    .eligible(true)
                    .memberId("INS456")
                    .planName("No Copay Plan")
                    .copayRequired(BigDecimal.ZERO)
                    .build();

            when(insuranceCache.getEligibility("MRN003", "PAYER2")).thenReturn(null);
            when(insuranceGateway.verifyEligibility("MRN003", "PAYER2")).thenReturn(eligibility);

            Map<String, Object> result = appointmentService.scheduleAppointment(
                    3L, "MRN003", "PAYER2", LocalDate.of(2026, 4, 1), "NEW_PATIENT", 10L);

            assertThat(result).containsEntry("status", "SCHEDULED");
            assertThat(result).containsEntry("copayAmount", BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("checkInsuranceEligibility")
    class CheckInsuranceEligibility {

        @Test
        @DisplayName("should return cached result on cache hit")
        void shouldReturnCachedResult() {
            InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
            cached.patientMrn = "MRN001";
            cached.patientSsn = "XXX-XX-0001";
            cached.payerId = "PAYER1";
            cached.memberId = "INS123";
            cached.eligible = true;
            cached.planName = "Premium Plan";
            cached.copay = "25.00";
            cached.deductible = "500.00";
            cached.cachedAt = LocalDateTime.now();

            when(insuranceCache.getEligibility("MRN001", "PAYER1")).thenReturn(cached);

            AppointmentService.EligibilityResult result =
                    appointmentService.checkInsuranceEligibility("MRN001", "PAYER1");

            assertThat(result.isEligible()).isTrue();
            assertThat(result.getMemberId()).isEqualTo("INS123");
            assertThat(result.getPlanName()).isEqualTo("Premium Plan");
            assertThat(result.getCopayRequired()).isEqualByComparingTo(new BigDecimal("25.00"));
            verify(insuranceGateway, never()).verifyEligibility(anyString(), anyString());
        }

        @Test
        @DisplayName("should call gateway on cache miss")
        void shouldCallGatewayOnCacheMiss() {
            AppointmentService.EligibilityResult gatewayResult = AppointmentService.EligibilityResult.builder()
                    .eligible(true)
                    .patientSsn("XXX-XX-0001")
                    .memberId("INS123")
                    .planName("Premium Plan")
                    .copayRequired(new BigDecimal("25.00"))
                    .deductibleRemaining(new BigDecimal("500.00"))
                    .build();

            when(insuranceCache.getEligibility("MRN001", "PAYER1")).thenReturn(null);
            when(insuranceGateway.verifyEligibility("MRN001", "PAYER1")).thenReturn(gatewayResult);

            AppointmentService.EligibilityResult result =
                    appointmentService.checkInsuranceEligibility("MRN001", "PAYER1");

            assertThat(result.isEligible()).isTrue();
            verify(insuranceGateway).verifyEligibility("MRN001", "PAYER1");
            verify(insuranceCache).cacheEligibility(
                    eq("MRN001"), eq("XXX-XX-0001"), eq("PAYER1"),
                    eq("INS123"), eq(true), eq("Premium Plan"),
                    eq("25.00"), eq("500.00"));
        }

        @Test
        @DisplayName("should not cache ineligible results")
        void shouldNotCacheIneligibleResults() {
            AppointmentService.EligibilityResult gatewayResult = AppointmentService.EligibilityResult.builder()
                    .eligible(false)
                    .reason("Coverage terminated")
                    .build();

            when(insuranceCache.getEligibility("MRN002", "PAYER1")).thenReturn(null);
            when(insuranceGateway.verifyEligibility("MRN002", "PAYER1")).thenReturn(gatewayResult);

            AppointmentService.EligibilityResult result =
                    appointmentService.checkInsuranceEligibility("MRN002", "PAYER1");

            assertThat(result.isEligible()).isFalse();
            verify(insuranceCache, never()).cacheEligibility(
                    anyString(), anyString(), anyString(), anyString(),
                    anyBoolean(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("EligibilityResult.fromCache")
    class EligibilityResultFromCache {

        @Test
        @DisplayName("should correctly map cached eligibility to result")
        void shouldMapCachedToResult() {
            InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
            cached.eligible = true;
            cached.patientSsn = "XXX-XX-1234";
            cached.memberId = "MEM789";
            cached.planName = "Gold Plan";
            cached.copay = "30.00";
            cached.deductible = "1000.00";

            AppointmentService.EligibilityResult result =
                    AppointmentService.EligibilityResult.fromCache(cached);

            assertThat(result.isEligible()).isTrue();
            assertThat(result.getPatientSsn()).isEqualTo("XXX-XX-1234");
            assertThat(result.getMemberId()).isEqualTo("MEM789");
            assertThat(result.getPlanName()).isEqualTo("Gold Plan");
            assertThat(result.getCopayRequired()).isEqualByComparingTo(new BigDecimal("30.00"));
            assertThat(result.getDeductibleRemaining()).isEqualByComparingTo(new BigDecimal("1000.00"));
        }

        @Test
        @DisplayName("should handle null copay and deductible")
        void shouldHandleNullCopayAndDeductible() {
            InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
            cached.eligible = true;
            cached.copay = null;
            cached.deductible = null;

            AppointmentService.EligibilityResult result =
                    AppointmentService.EligibilityResult.fromCache(cached);

            assertThat(result.getCopayRequired()).isNull();
            assertThat(result.getDeductibleRemaining()).isNull();
        }
    }
}
