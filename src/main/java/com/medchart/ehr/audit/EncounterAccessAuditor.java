package com.medchart.ehr.audit;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Records encounter access against the real patient (AuditAspect would attribute
 * the encounter id as the patient id). Never writes SSN or other PHI to app logs.
 */
@Component
@RequiredArgsConstructor
public class EncounterAccessAuditor {

    private static final String RESOURCE_TYPE = "Encounter";

    private final AuditService auditService;

    public void logEncounterAccess(Encounter encounter, AuditAction action, String description) {
        auditService.saveAuditEventAsync(encounterEvent(encounter, action, description).build());
    }

    public void logEncounterAccessFailure(Encounter encounter, AuditAction action, String description, Exception error) {
        auditService.saveAuditEventAsync(encounterEvent(encounter, action, description)
                .success(false)
                .errorMessage(error.getClass().getSimpleName())
                .build());
    }

    /** Patient-scoped lists are READs of that patient's record; cross-patient lists are SEARCHes. */
    public void logEncounterListAccess(Long patientId, Collection<Encounter> encounters, String description) {
        auditService.saveAuditEventAsync(listEvent(patientId, encounters, description).build());
    }

    public void logEncounterListAccessFailure(Long patientId, Collection<Encounter> encounters, String description,
                                              Exception error) {
        auditService.saveAuditEventAsync(listEvent(patientId, encounters, description)
                .success(false)
                .errorMessage(error.getClass().getSimpleName())
                .build());
    }

    private AuditEvent.AuditEventBuilder encounterEvent(Encounter encounter, AuditAction action, String description) {
        Patient patient = encounter.getPatient();
        return baseEvent(action, description)
                .patientId(patient != null ? patient.getId() : null)
                .patientMrn(patient != null ? patient.getMrn() : null)
                .resourceId(encounter.getId());
    }

    private AuditEvent.AuditEventBuilder listEvent(Long patientId, Collection<Encounter> encounters, String description) {
        long patientCount = encounters.stream()
                .map(Encounter::getPatient)
                .filter(p -> p != null && p.getId() != null)
                .map(Patient::getId)
                .distinct()
                .count();
        AuditAction action = patientId != null ? AuditAction.READ : AuditAction.SEARCH;
        return baseEvent(action,
                description + " [" + encounters.size() + " encounters, " + patientCount + " patients]")
                .patientId(patientId);
    }

    private AuditEvent.AuditEventBuilder baseEvent(AuditAction action, String description) {
        HttpServletRequest request = currentRequest();
        return AuditEvent.builder()
                .userId("system")
                .userName("System User")
                .action(action)
                .resourceType(RESOURCE_TYPE)
                .description(description)
                .ipAddress(request != null ? clientIp(request) : null)
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .timestamp(LocalDateTime.now())
                .success(true);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
