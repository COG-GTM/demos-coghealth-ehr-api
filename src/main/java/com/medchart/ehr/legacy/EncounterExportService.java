package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EncounterExportService {

    private static final Pattern EXPORT_FILE_NAME = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*");

    private final EntityManager entityManager;
    private final AuditService auditService;
    private final String exportDirectory;

    public EncounterExportService(EntityManager entityManager,
                                  AuditService auditService,
                                  @Value("${ehr.export.directory:${java.io.tmpdir}}") String exportDirectory) {
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.exportDirectory = exportDirectory;
    }

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

    /**
     * Writes a CSV of all active patients into the configured export directory.
     *
     * @param fileName a bare file name; it is resolved inside {@code ehr.export.directory} and may not
     *                 traverse outside of it
     * @return the path that was written
     */
    public Path exportAllPatientsToFile(String fileName) {
        Path target = resolveExportPath(fileName);

        Query query = entityManager.createNativeQuery(
            "SELECT id, mrn, first_name, last_name, date_of_birth, " +
            "email, phone_home, phone_mobile, street1, city, state, zip_code " +
            "FROM patients WHERE active = true");
        
        List<Object[]> patients = query.getResultList();
        
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            log.error("Failed to create export directory", e);
            throw new RuntimeException("Export failed", e);
        }

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(target))) {
            writer.println("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip");
            for (Object[] p : patients) {
                writer.println(String.join(",", 
                    String.valueOf(p[0]), String.valueOf(p[1]), String.valueOf(p[2]),
                    String.valueOf(p[3]), String.valueOf(p[4]), String.valueOf(p[5]),
                    String.valueOf(p[6]), String.valueOf(p[7]), String.valueOf(p[8]),
                    String.valueOf(p[9]), String.valueOf(p[10]), String.valueOf(p[11])));
            }
            log.info("Exported {} patients to file: {}", patients.size(), target.getFileName());
        } catch (IOException e) {
            log.error("Failed to export patients to file", e);
            throw new RuntimeException("Export failed", e);
        }

        auditBulkExport(target, patients.size());
        return target;
    }

    Path resolveExportPath(String fileName) {
        if (fileName == null || !EXPORT_FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Invalid export file name");
        }

        Path baseDir = Paths.get(exportDirectory).toAbsolutePath().normalize();
        Path resolved;
        try {
            resolved = baseDir.resolve(fileName).normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid export file name", e);
        }

        if (!baseDir.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Invalid export file name");
        }
        return resolved;
    }

    private void auditBulkExport(Path target, int patientCount) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication != null ? authentication.getName() : "system";

        AuditEvent event = new AuditEvent();
        event.setUserId(userId);
        event.setAction(AuditAction.EXPORT);
        event.setResourceType("PatientBulkExport");
        event.setDescription("Bulk patient export of " + patientCount + " records to " + target.getFileName());
        event.setTimestamp(LocalDateTime.now());
        event.setSuccess(true);
        auditService.saveAuditEventAsync(event);
    }
}
