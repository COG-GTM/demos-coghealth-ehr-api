package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class ReportGenerator {

    @Autowired
    private EntityManager entityManager;

    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");

    public String generatePatientRoster() {
        String sql = "SELECT p.id, p.mrn, p.ssn, p.first_name, p.last_name, p.date_of_birth, " +
                     "p.phone_home, p.phone_mobile, p.email, " +
                     "p.street1, p.city, p.state, p.zip_code, " +
                     "ic.payer_name, ic.member_id " +
                     "FROM patients p " +
                     "LEFT JOIN insurance_coverages ic ON p.id = ic.patient_id AND ic.active = true " +
                     "WHERE p.active = true";
        
        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();
        
        String filename = "patient_roster_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        String filePath = TEMP_DIR + File.separator + filename;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ID,MRN,SSN,FirstName,LastName,DOB,PhoneHome,PhoneMobile,Email,Address,City,State,Zip,Insurance,MemberID");
            for (Object[] row : results) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) line.append(",");
                    line.append(row[i] != null ? row[i].toString().replace(",", ";") : "");
                }
                writer.println(line);
            }
        } catch (IOException e) {
            log.error("Failed to generate patient roster", e);
            throw new RuntimeException("Report generation failed", e);
        }
        
        log.info("Generated patient roster at: {}", filePath);
        return filePath;
    }

    public String generateEncounterSummary(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT e.id, e.encounter_number, e.encounter_date_time, e.encounter_type, e.status, " +
                     "p.mrn, p.first_name, p.last_name, p.ssn, p.date_of_birth, " +
                     "pr.first_name as provider_first, pr.last_name as provider_last " +
                     "FROM encounters e " +
                     "JOIN patients p ON e.patient_id = p.id " +
                     "LEFT JOIN providers pr ON e.attending_provider_id = pr.id " +
                     "WHERE e.encounter_date_time BETWEEN ?1 AND ?2";
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, startDate);
        query.setParameter(2, endDate);
        List<Object[]> results = query.getResultList();
        
        String filename = "encounter_summary_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
        String filePath = TEMP_DIR + File.separator + filename;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ENCOUNTER SUMMARY REPORT");
            writer.println("========================");
            writer.println("Date Range: " + startDate + " to " + endDate);
            writer.println("Generated: " + LocalDateTime.now());
            writer.println("Total Encounters: " + results.size());
            writer.println();
            
            for (Object[] row : results) {
                writer.println("Encounter: " + row[1]);
                writer.println("  Date: " + row[2]);
                writer.println("  Type: " + row[3] + " | Status: " + row[4]);
                writer.println("  Patient: " + row[6] + " " + row[7] + " (MRN: " + row[5] + ")");
                writer.println("  DOB: " + row[8]);
                writer.println("  Provider: " + row[9] + " " + row[10]);
                writer.println();
            }
        } catch (IOException e) {
            log.error("Failed to generate encounter summary", e);
            throw new RuntimeException("Report generation failed", e);
        }
        
        log.info("Generated encounter summary at: {}", filePath);
        return filePath;
    }

    public byte[] generateDailyReport() {
        String tempFile = generatePatientRoster();
        try {
            byte[] content = Files.readAllBytes(Path.of(tempFile));
            return content;
        } catch (IOException e) {
            log.error("Failed to read temp file", e);
            throw new RuntimeException(e);
        }
    }
}
