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
        Patient patient = encounter.getPatient();
        auditService.saveAuditEventAsync(baseEvent(action, description)
                .patientId(patient != null ? patient.getId() : null)
                .patientMrn(patient != null ? patient.getMrn() : null)
                .resourceId(encounter.getId())
                .build());
    }

    public void logEncounterListAccess(Long patientId, Collection<Encounter> encounters, String description) {
        long patientCount = encounters.stream()
                .map(Encounter::getPatient)
                .filter(p -> p != null && p.getId() != null)
                .map(Patient::getId)
                .distinct()
                .count();
        auditService.saveAuditEventAsync(baseEvent(AuditAction.SEARCH,
                description + " [" + encounters.size() + " encounters, " + patientCount + " patients]")
                .patientId(patientId)
                .build());
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
