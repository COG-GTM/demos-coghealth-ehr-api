package com.medchart.ehr.audit;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Audits encounter reads/writes against the encounter's patient. {@link AuditAccess} can't be used
 * here because it would file the encounter/provider id argument as the patient id.
 */
@Component
@RequiredArgsConstructor
public class EncounterAccessAuditor {

    private static final String RESOURCE_TYPE = "Encounter";

    private final AuditService auditService;

    public void logAccess(Encounter encounter, AuditAction action, String description) {
        Patient patient = encounter.getPatient();
        auditService.saveAuditEventAsync(baseEvent(action, description)
                .patientId(patient != null ? patient.getId() : null)
                .patientMrn(patient != null ? patient.getMrn() : null)
                .resourceId(encounter.getId())
                .build());
    }

    /** Patient-scoped lists are READs of that patient's record; cross-patient lists are SEARCHes. */
    public void logListAccess(Long patientId, List<Encounter> encounters, String description) {
        long patients = encounters.stream()
                .map(Encounter::getPatient)
                .filter(Objects::nonNull)
                .map(Patient::getId)
                .distinct()
                .count();
        auditService.saveAuditEventAsync(baseEvent(patientId != null ? AuditAction.READ : AuditAction.SEARCH,
                description + " [" + encounters.size() + " encounters, " + patients + " patients]")
                .patientId(patientId)
                .build());
    }

    private AuditEvent.AuditEventBuilder baseEvent(AuditAction action, String description) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth != null && auth.isAuthenticated() ? auth.getName() : "anonymous";
        HttpServletRequest request = currentRequest();
        return AuditEvent.builder()
                .userId(user)
                .userName(user)
                .action(action)
                .resourceType(RESOURCE_TYPE)
                .description(description)
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .sessionId(request != null && request.getSession(false) != null ? request.getSession(false).getId() : null)
                .timestamp(LocalDateTime.now())
                .success(true);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes ? ((ServletRequestAttributes) attrs).getRequest() : null;
    }
}
