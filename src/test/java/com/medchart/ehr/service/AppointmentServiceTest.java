package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.legacy.InsuranceCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final String MRN = "MRN-TEST-0001";
    private static final String PAYER = "PAYER-A";

    @Mock
    private InsuranceCache insuranceCache;
    @Mock
    private InsuranceGateway insuranceGateway;
    @InjectMocks
    private AppointmentService service;

    private static AppointmentService.EligibilityResult eligible(String copay) {
        return AppointmentService.EligibilityResult.builder()
                .eligible(true).memberId("M1").planName("Plan")
                .patientSsn("XXX-XX-0001")
                .copayRequired(copay == null ? null : new BigDecimal(copay))
                .deductibleRemaining(new BigDecimal("500.00"))
                .build();
    }

    private static InsuranceCache.CachedEligibility cached(Instant at) {
        InsuranceCache.CachedEligibility c = new InsuranceCache.CachedEligibility();
        c.patientMrn = MRN;
        c.payerId = PAYER;
        c.memberId = "M-CACHED";
        c.planName = "Cached Plan";
        c.eligible = true;
        c.copay = "10.00";
        c.deductible = "0.00";
        c.cachedAt = at;
        return c;
    }

    @Test
    @DisplayName("BR-SVC-09: scheduling an appointment is audited as an Appointment CREATE")
    void brSvc09_schedulingIsAudited() throws Exception {
        AuditAccess a = AppointmentService.class.getMethod("scheduleAppointment", Long.class, String.class,
                String.class, LocalDate.class, String.class, Long.class).getAnnotation(AuditAccess.class);
        assertEquals(AuditAction.CREATE, a.action());
        assertEquals("Appointment", a.resourceType());
    }

    @Test
    @DisplayName("BR-SVC-09: an eligible patient is scheduled with eligibility verified and copay recorded")
    void brSvc09_eligiblePatientScheduled() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible("25.00"));
        LocalDate date = LocalDate.of(2026, 3, 2);

        Map<String, Object> appt = service.scheduleAppointment(7L, MRN, PAYER, date, "FOLLOW_UP", 3L);

        assertEquals("SCHEDULED", appt.get("status"));
        assertEquals(Boolean.TRUE, appt.get("eligibilityVerified"));
        assertEquals(new BigDecimal("25.00"), appt.get("copayAmount"));
        assertEquals(7L, appt.get("patientId"));
        assertEquals(MRN, appt.get("patientMrn"));
        assertEquals(date, appt.get("appointmentDate"));
        assertEquals("FOLLOW_UP", appt.get("appointmentType"));
        assertEquals(3L, appt.get("providerId"));
    }

    @Test
    @DisplayName("BR-SVC-09: zero or missing copay still schedules (copay boundary)")
    void brSvc09_zeroAndMissingCopay() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible("0.00"));
        assertEquals(new BigDecimal("0.00"), service.scheduleAppointment(1L, MRN, PAYER, LocalDate.now(), "NEW", 1L).get("copayAmount"));

        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible(null));
        assertNull(service.scheduleAppointment(1L, MRN, PAYER, LocalDate.now(), "NEW", 1L).get("copayAmount"));
    }

    @Test
    @DisplayName("BR-SVC-09: an ineligible patient cannot be scheduled and the payer reason is surfaced")
    void brSvc09_ineligiblePatientRejected() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(
                AppointmentService.EligibilityResult.builder().eligible(false).reason("Coverage terminated").build());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.scheduleAppointment(1L, MRN, PAYER, LocalDate.now(), "NEW", 1L));
        assertEquals("Patient insurance not eligible: Coverage terminated", e.getMessage());
    }

    @Test
    @DisplayName("BR-SVC-10: a fresh cache entry is used and the payer gateway is not called")
    void brSvc10_freshCacheHitSkipsGateway() {
        when(insuranceCache.getEligibility(MRN, PAYER)).thenReturn(cached(Instant.now().minus(Duration.ofHours(1))));

        AppointmentService.EligibilityResult r = service.checkInsuranceEligibility(MRN, PAYER);

        assertTrue(r.isEligible());
        assertEquals("M-CACHED", r.getMemberId());
        assertEquals("Cached Plan", r.getPlanName());
        assertEquals(new BigDecimal("10.00"), r.getCopayRequired());
        assertEquals(new BigDecimal("0.00"), r.getDeductibleRemaining());
        verifyNoInteractions(insuranceGateway);
    }

    @Test
    @DisplayName("BR-SVC-10: a cache miss calls the gateway and caches the eligible result")
    void brSvc10_cacheMissCallsGatewayAndCaches() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible("25.00"));

        AppointmentService.EligibilityResult r = service.checkInsuranceEligibility(MRN, PAYER);

        assertTrue(r.isEligible());
        verify(insuranceCache).cacheEligibility(MRN, null, PAYER, "M1", true, "Plan", "25.00", "500.00");
    }

    @Test
    @DisplayName("BR-SVC-10: cached copay/deductible may be absent and map to null amounts")
    void brSvc10_cachedNullAmounts() {
        InsuranceCache.CachedEligibility c = cached(Instant.now());
        c.copay = null;
        c.deductible = null;
        AppointmentService.EligibilityResult r = AppointmentService.EligibilityResult.fromCache(c);
        assertNull(r.getCopayRequired());
        assertNull(r.getDeductibleRemaining());
    }

    @Test
    @DisplayName("BR-SVC-11: cached eligibility older than the 24h TTL is re-verified with the payer")
    void brSvc11_staleCacheReverified() {
        when(insuranceCache.getEligibility(MRN, PAYER))
                .thenReturn(cached(Instant.now().minus(AppointmentService.ELIGIBILITY_TTL).minus(Duration.ofMinutes(1))));
        AppointmentService.EligibilityResult fresh = eligible("25.00");
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(fresh);

        assertSame(fresh, service.checkInsuranceEligibility(MRN, PAYER));
        verify(insuranceGateway).verifyEligibility(MRN, PAYER);
    }

    @Test
    @DisplayName("BR-SVC-11: entries just inside the 24h TTL are still served from cache (boundary)")
    void brSvc11_cacheJustInsideTtlServed() {
        assertEquals(24, AppointmentService.ELIGIBILITY_TTL.toHours());
        when(insuranceCache.getEligibility(MRN, PAYER))
                .thenReturn(cached(Instant.now().minus(Duration.ofHours(23).plusMinutes(59))));
        service.checkInsuranceEligibility(MRN, PAYER);
        verifyNoInteractions(insuranceGateway);
    }

    @Test
    @DisplayName("BR-SVC-11: entries without a cached-at timestamp are treated as stale")
    void brSvc11_missingTimestampIsStale() {
        when(insuranceCache.getEligibility(MRN, PAYER)).thenReturn(cached(null));
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible("1.00"));
        service.checkInsuranceEligibility(MRN, PAYER);
        verify(insuranceGateway).verifyEligibility(MRN, PAYER);
    }

    @Test
    @DisplayName("BR-SVC-12: the patient SSN is never written to the eligibility cache")
    void brSvc12_ssnNeverCached() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(eligible("25.00"));
        service.checkInsuranceEligibility(MRN, PAYER);
        verify(insuranceCache).cacheEligibility(anyString(), isNull(), anyString(), anyString(), anyBoolean(),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("BR-SVC-13: ineligible payer responses are not cached")
    void brSvc13_ineligibleNotCached() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(
                AppointmentService.EligibilityResult.builder().eligible(false).build());
        assertFalse(service.checkInsuranceEligibility(MRN, PAYER).isEligible());
        verify(insuranceCache, never()).cacheEligibility(any(), any(), any(), any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("BR-SVC-13: eligible responses without amounts cache null copay/deductible")
    void brSvc13_eligibleWithoutAmounts() {
        when(insuranceGateway.verifyEligibility(MRN, PAYER)).thenReturn(
                AppointmentService.EligibilityResult.builder().eligible(true).memberId("M2").planName("P").build());
        service.checkInsuranceEligibility(MRN, PAYER);
        verify(insuranceCache).cacheEligibility(MRN, null, PAYER, "M2", true, "P", null, null);
    }
}
