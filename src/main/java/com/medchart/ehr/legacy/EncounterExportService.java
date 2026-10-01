package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EncounterExportService {

    private static final String UNKNOWN_ROLE = "UNKNOWN";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PatientAccessLogger accessLogger;

    public byte[] exportEncountersForDateRange(LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT e.id, e.encounter_number, e.encounter_type, e.status, e.encounter_date_time, " +
                     "p.mrn, p.first_name, p.last_name, p.date_of_birth " +
                     "FROM encounters e " +
                     "JOIN patients p ON e.patient_id = p.id " +
                     "WHERE e.encounter_date_time BETWEEN ?1 AND ?2";
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, startDate.atStartOfDay());
        query.setParameter(2, endDate.plusDays(1).atStartOfDay());
        
        List<Object[]> results = query.getResultList();
        
        StringBuilder csv = new StringBuilder();
        csv.append("EncounterId,EncounterNumber,PatientMRN,PatientName,DOB,EncounterDate,Type,Status\n");
        
        for (Object[] row : results) {
            csv.append(row[0]).append(",");
            csv.append(row[1]).append(",");
            csv.append(row[5]).append(",");
            csv.append(row[6]).append(" ").append(row[7]).append(",");
            csv.append(row[8]).append(",");
            csv.append(row[4]).append(",");
            csv.append(row[2]).append(",");
            csv.append(row[3]).append("\n");
        }
        
        accessLogger.logBulkAccess(
            currentUserId(),
            currentUserRole(),
            AuditAction.EXPORT,
            "Encounter",
            results.size(),
            "Encounter CSV export for date range " + startDate + " to " + endDate,
            clientIpAddress());

        log.info("Exported {} encounters for date range {} to {}", results.size(), startDate, endDate);
        return csv.toString().getBytes();
    }

    public byte[] exportPatientEncounterHistory(Long patientId) {
        Query patientQuery = entityManager.createNativeQuery(
            "SELECT mrn, first_name, last_name, ssn, date_of_birth FROM patients WHERE id = ?1");
        patientQuery.setParameter(1, patientId);
        Object[] patientData = (Object[]) patientQuery.getSingleResult();
        
        Query encounterQuery = entityManager.createNativeQuery(
            "SELECT * FROM encounters WHERE patient_id = ?1 ORDER BY encounter_date_time DESC");
        encounterQuery.setParameter(1, patientId);
        List<Object[]> encounters = encounterQuery.getResultList();

        accessLogger.logAccess(
            currentUserId(),
            currentUserRole(),
            patientId,
            String.valueOf(patientData[0]),
            AuditAction.EXPORT,
            "Encounter",
            "Patient encounter history export (" + encounters.size() + " encounters)",
            clientIpAddress(),
            currentSessionId());

        StringBuilder export = new StringBuilder();
        export.append("Patient Encounter History Report\n");
        export.append("Generated: ").append(LocalDateTime.now()).append("\n\n");
        export.append("Patient: ").append(patientData[1]).append(" ").append(patientData[2]).append("\n");
        export.append("MRN: ").append(patientData[0]).append("\n");
        export.append("SSN: ").append(patientData[3]).append("\n");
        export.append("DOB: ").append(patientData[4]).append("\n\n");
        export.append("Encounters:\n");
        export.append("-".repeat(80)).append("\n");
        
        for (Object[] enc : encounters) {
            export.append("Encounter #: ").append(enc[1]).append("\n");
            export.append("Date: ").append(enc[5]).append("\n");
            export.append("Type: ").append(enc[3]).append("\n");
            export.append("Status: ").append(enc[4]).append("\n");
            export.append("-".repeat(40)).append("\n");
        }
        
        return export.toString().getBytes();
    }

    public void exportAllPatientsToFile(String filePath) {
        Query query = entityManager.createNativeQuery(
            "SELECT id, mrn, first_name, last_name, date_of_birth, " +
            "email, phone_home, phone_mobile, street1, city, state, zip_code " +
            "FROM patients WHERE active = true");
        
        List<Object[]> patients = query.getResultList();

        accessLogger.logBulkAccess(
            currentUserId(),
            currentUserRole(),
            AuditAction.EXPORT,
            "Patient",
            patients.size(),
            "Active patient demographics export to file",
            clientIpAddress());

        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip");
            for (Object[] p : patients) {
                writer.println(String.join(",", 
                    String.valueOf(p[0]), String.valueOf(p[1]), String.valueOf(p[2]),
                    String.valueOf(p[3]), String.valueOf(p[4]), String.valueOf(p[5]),
                    String.valueOf(p[6]), String.valueOf(p[7]), String.valueOf(p[8]),
                    String.valueOf(p[9]), String.valueOf(p[10]), String.valueOf(p[11])));
            }
            log.info("Exported {} patients to file: {}", patients.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to export patients to file", e);
            throw new RuntimeException("Export failed", e);
        }
    }

    private Long currentUserId() {
        Object principal = currentPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        return null;
    }

    private String currentUserRole() {
        Authentication authentication = currentAuthentication();
        if (authentication == null) {
            return UNKNOWN_ROLE;
        }
        String roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));
        if (roles.isEmpty()) {
            return authentication.getName() != null ? authentication.getName() : UNKNOWN_ROLE;
        }
        return roles;
    }

    private Object currentPrincipal() {
        Authentication authentication = currentAuthentication();
        return authentication != null ? authentication.getPrincipal() : null;
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext() != null
            ? SecurityContextHolder.getContext().getAuthentication()
            : null;
    }

    private String clientIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String currentSessionId() {
        HttpServletRequest request = currentRequest();
        if (request == null || request.getSession(false) == null) {
            return null;
        }
        return request.getSession(false).getId();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
