package com.medchart.ehr.service;

import com.medchart.ehr.legacy.InsuranceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private InsuranceCache insuranceCache;

    @Mock
    private InsuranceGateway insuranceGateway;

    @InjectMocks
    private AppointmentService appointmentService;

    private AppointmentService.EligibilityResult eligibleResult;

    @BeforeEach
    void setUp() {
        eligibleResult = AppointmentService.EligibilityResult.builder()
                .eligible(true)
                .memberId("INS12345")
                .planName("Premium Health Plan")
                .copayRequired(new BigDecimal("25.00"))
                .deductibleRemaining(new BigDecimal("500.00"))
                .patientSsn("XXX-XX-0001")
                .build();
    }

    @Test
    void scheduleAppointment_eligiblePatient_returnsAppointment() {
        when(insuranceCache.getEligibility("MRN001", "PAY001")).thenReturn(null);
        when(insuranceGateway.verifyEligibility("MRN001", "PAY001")).thenReturn(eligibleResult);

        Map<String, Object> result = appointmentService.scheduleAppointment(
                1L, "MRN001", "PAY001", LocalDate.of(2026, 6, 1), "OFFICE_VISIT", 10L);

        assertNotNull(result);
        assertEquals(1L, result.get("patientId"));
        assertEquals("MRN001", result.get("patientMrn"));
        assertEquals("SCHEDULED", result.get("status"));
        assertTrue((Boolean) result.get("eligibilityVerified"));
        assertEquals(new BigDecimal("25.00"), result.get("copayAmount"));
    }

    @Test
    void scheduleAppointment_ineligiblePatient_throwsException() {
        AppointmentService.EligibilityResult ineligible = AppointmentService.EligibilityResult.builder()
                .eligible(false)
                .reason("Coverage expired")
                .build();

        when(insuranceCache.getEligibility("MRN001", "PAY001")).thenReturn(null);
        when(insuranceGateway.verifyEligibility("MRN001", "PAY001")).thenReturn(ineligible);

        assertThrows(IllegalStateException.class,
                () -> appointmentService.scheduleAppointment(
                        1L, "MRN001", "PAY001", LocalDate.of(2026, 6, 1), "OFFICE_VISIT", 10L));
    }

    @Test
    void checkInsuranceEligibility_cacheMiss_callsGateway() {
        when(insuranceCache.getEligibility("MRN001", "PAY001")).thenReturn(null);
        when(insuranceGateway.verifyEligibility("MRN001", "PAY001")).thenReturn(eligibleResult);

        AppointmentService.EligibilityResult result = appointmentService.checkInsuranceEligibility("MRN001", "PAY001");

        assertTrue(result.isEligible());
        verify(insuranceGateway).verifyEligibility("MRN001", "PAY001");
        verify(insuranceCache).cacheEligibility(
                eq("MRN001"), eq("XXX-XX-0001"), eq("PAY001"),
                eq("INS12345"), eq(true), eq("Premium Health Plan"),
                eq("25.00"), eq("500.00"));
    }

    @Test
    void checkInsuranceEligibility_cacheHit_skipsGateway() {
        InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
        cached.patientMrn = "MRN001";
        cached.payerId = "PAY001";
        cached.memberId = "INS12345";
        cached.eligible = true;
        cached.planName = "Premium Health Plan";
        cached.copay = "25.00";
        cached.deductible = "500.00";
        cached.cachedAt = LocalDateTime.now();

        when(insuranceCache.getEligibility("MRN001", "PAY001")).thenReturn(cached);

        AppointmentService.EligibilityResult result = appointmentService.checkInsuranceEligibility("MRN001", "PAY001");

        assertTrue(result.isEligible());
        assertEquals("INS12345", result.getMemberId());
        verify(insuranceGateway, never()).verifyEligibility(any(), any());
    }

    @Test
    void checkInsuranceEligibility_ineligibleResult_doesNotCache() {
        AppointmentService.EligibilityResult ineligible = AppointmentService.EligibilityResult.builder()
                .eligible(false)
                .reason("Coverage expired")
                .build();

        when(insuranceCache.getEligibility("MRN001", "PAY001")).thenReturn(null);
        when(insuranceGateway.verifyEligibility("MRN001", "PAY001")).thenReturn(ineligible);

        AppointmentService.EligibilityResult result = appointmentService.checkInsuranceEligibility("MRN001", "PAY001");

        assertFalse(result.isEligible());
        verify(insuranceCache, never()).cacheEligibility(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void eligibilityResult_fromCache_convertsCorrectly() {
        InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
        cached.eligible = true;
        cached.patientSsn = "XXX-XX-0001";
        cached.memberId = "INS12345";
        cached.planName = "Premium Health Plan";
        cached.copay = "25.00";
        cached.deductible = "500.00";

        AppointmentService.EligibilityResult result = AppointmentService.EligibilityResult.fromCache(cached);

        assertTrue(result.isEligible());
        assertEquals("INS12345", result.getMemberId());
        assertEquals("Premium Health Plan", result.getPlanName());
        assertEquals(new BigDecimal("25.00"), result.getCopayRequired());
        assertEquals(new BigDecimal("500.00"), result.getDeductibleRemaining());
    }

    @Test
    void eligibilityResult_fromCache_handlesNullAmounts() {
        InsuranceCache.CachedEligibility cached = new InsuranceCache.CachedEligibility();
        cached.eligible = true;
        cached.memberId = "INS12345";
        cached.copay = null;
        cached.deductible = null;

        AppointmentService.EligibilityResult result = AppointmentService.EligibilityResult.fromCache(cached);

        assertTrue(result.isEligible());
        assertNull(result.getCopayRequired());
        assertNull(result.getDeductibleRemaining());
    }
}
