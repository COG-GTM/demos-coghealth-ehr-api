package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
public class EncounterExportService {

    @Autowired
    private EntityManager entityManager;

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
            csv.append(CsvEncoder.row(row[0], row[1], row[5], row[6] + " " + row[7],
                    row[8], row[4], row[2], row[3])).append("\n");
        }
        
        log.info("Exported {} encounters for date range {} to {}", results.size(), startDate, endDate);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
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

    public void exportAllPatientsToFile(String filePath) {
        Query query = entityManager.createNativeQuery(
            "SELECT id, mrn, first_name, last_name, date_of_birth, " +
            "email, phone_home, phone_mobile, street1, city, state, zip_code " +
            "FROM patients WHERE active = true");
        
        List<Object[]> patients = query.getResultList();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("ID,MRN,FirstName,LastName,DOB,Email,PhoneHome,PhoneMobile,Street,City,State,Zip");
            for (Object[] p : patients) {
                writer.println(CsvEncoder.row(p));
            }
            log.info("Exported {} patients to file: {}", patients.size(), filePath);
        } catch (IOException e) {
            log.error("Failed to export patients to file", e);
            throw new RuntimeException("Export failed", e);
        }
    }
}
