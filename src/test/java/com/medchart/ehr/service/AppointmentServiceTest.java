package com.medchart.ehr.service;

import com.medchart.ehr.legacy.InsuranceCache;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final String MRN = "MRN0001";
    private static final String PAYER_ID = "PAYER-BCBS";

    @Mock
    private InsuranceCache insuranceCache;

    @Mock
    private InsuranceGateway insuranceGateway;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void scheduleAppointmentReturnsVerifiedAppointmentWhenEligible() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(eligible(new BigDecimal("25.00")));

        LocalDate date = LocalDate.of(2026, 1, 15);
        Map<String, Object> appointment = appointmentService.scheduleAppointment(
            7L, MRN, PAYER_ID, date, "OFFICE_VISIT", 42L);

        assertThat(appointment)
            .containsEntry("patientId", 7L)
            .containsEntry("patientMrn", MRN)
            .containsEntry("appointmentDate", date)
            .containsEntry("appointmentType", "OFFICE_VISIT")
            .containsEntry("providerId", 42L)
            .containsEntry("eligibilityVerified", true)
            .containsEntry("copayAmount", new BigDecimal("25.00"))
            .containsEntry("status", "SCHEDULED");
    }

    @Test
    void scheduleAppointmentRejectsIneligiblePatient() {
        AppointmentService.EligibilityResult result = AppointmentService.EligibilityResult.builder()
            .eligible(false)
            .reason("Coverage terminated")
            .build();
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID)).thenReturn(result);

        assertThatThrownBy(() -> appointmentService.scheduleAppointment(
            7L, MRN, PAYER_ID, LocalDate.of(2026, 1, 15), "OFFICE_VISIT", 42L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Coverage terminated");

        verify(insuranceCache, never()).cacheEligibility(
            anyString(), anyString(), anyString(), anyString(), anyBoolean(),
            anyString(), anyString(), anyString());
    }

    @Test
    void scheduleAppointmentAcceptsZeroCopay() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(eligible(BigDecimal.ZERO));

        Map<String, Object> appointment = appointmentService.scheduleAppointment(
            7L, MRN, PAYER_ID, LocalDate.of(2026, 1, 15), "OFFICE_VISIT", 42L);

        assertThat(appointment).containsEntry("copayAmount", BigDecimal.ZERO);
    }

    @Test
    void scheduleAppointmentAcceptsNullCopay() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(eligible(null));

        Map<String, Object> appointment = appointmentService.scheduleAppointment(
            7L, MRN, PAYER_ID, LocalDate.of(2026, 1, 15), "OFFICE_VISIT", 42L);

        assertThat(appointment).containsEntry("eligibilityVerified", true);
        assertThat(appointment.get("copayAmount")).isNull();
    }

    @Test
    void checkInsuranceEligibilityUsesFreshCacheEntry() {
        when(insuranceCache.getEligibility(MRN, PAYER_ID))
            .thenReturn(cachedEntry(LocalDateTime.now().minusHours(1)));

        AppointmentService.EligibilityResult result =
            appointmentService.checkInsuranceEligibility(MRN, PAYER_ID);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getMemberId()).isEqualTo("MEM-1");
        assertThat(result.getCopayRequired()).isEqualByComparingTo("15.00");
        assertThat(result.getDeductibleRemaining()).isEqualByComparingTo("300.00");
        verifyNoInteractions(insuranceGateway);
    }

    @Test
    void checkInsuranceEligibilityRefreshesStaleCacheEntry() {
        when(insuranceCache.getEligibility(MRN, PAYER_ID))
            .thenReturn(cachedEntry(LocalDateTime.now().minus(AppointmentService.ELIGIBILITY_TTL).minusMinutes(1)));
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(eligible(new BigDecimal("25.00")));

        AppointmentService.EligibilityResult result =
            appointmentService.checkInsuranceEligibility(MRN, PAYER_ID);

        assertThat(result.getCopayRequired()).isEqualByComparingTo("25.00");
        verify(insuranceGateway).verifyEligibility(MRN, PAYER_ID);
    }

    @Test
    void checkInsuranceEligibilityCachesEligibleGatewayResult() {
        when(insuranceCache.getEligibility(MRN, PAYER_ID)).thenReturn(null);
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(eligible(new BigDecimal("25.00")));

        appointmentService.checkInsuranceEligibility(MRN, PAYER_ID);

        verify(insuranceCache).cacheEligibility(
            eq(MRN), any(), eq(PAYER_ID), eq("MEM-9"), eq(true),
            eq("Premium Health Plan"), eq("25.00"), eq("500.00"));
    }

    @Test
    void checkInsuranceEligibilityDoesNotCacheIneligibleResult() {
        when(insuranceCache.getEligibility(MRN, PAYER_ID)).thenReturn(null);
        when(insuranceGateway.verifyEligibility(MRN, PAYER_ID))
            .thenReturn(AppointmentService.EligibilityResult.builder()
                .eligible(false)
                .reason("Plan inactive")
                .build());

        AppointmentService.EligibilityResult result =
            appointmentService.checkInsuranceEligibility(MRN, PAYER_ID);

        assertThat(result.isEligible()).isFalse();
        verify(insuranceCache, never()).cacheEligibility(
            anyString(), anyString(), anyString(), anyString(), anyBoolean(),
            anyString(), anyString(), anyString());
    }

    private static AppointmentService.EligibilityResult eligible(BigDecimal copay) {
        return AppointmentService.EligibilityResult.builder()
            .eligible(true)
            .memberId("MEM-9")
            .planName("Premium Health Plan")
            .copayRequired(copay)
            .deductibleRemaining(new BigDecimal("500.00"))
            .patientSsn("XXX-XX-0001")
            .build();
    }

    private static InsuranceCache.CachedEligibility cachedEntry(LocalDateTime cachedAt) {
        InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
        cached.patientMrn = MRN;
        cached.payerId = PAYER_ID;
        cached.memberId = "MEM-1";
        cached.eligible = true;
        cached.planName = "Premium Health Plan";
        cached.copay = "15.00";
        cached.deductible = "300.00";
        cached.cachedAt = cachedAt;
        return cached;
    }
}
