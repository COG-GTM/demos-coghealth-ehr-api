package com.medchart.ehr.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ReportGenerator {

    private static final int SSN_COLUMN_INDEX = 2;

    @Autowired
    private EntityManager entityManager;

    public byte[] generatePatientRoster() {
        String sql = "SELECT p.id, p.mrn, p.ssn, p.first_name, p.last_name, p.date_of_birth, " +
                     "p.phone_home, p.phone_mobile, p.email, " +
                     "p.street1, p.city, p.state, p.zip_code, " +
                     "ic.payer_name, ic.member_id " +
                     "FROM patients p " +
                     "LEFT JOIN insurance_coverages ic ON p.id = ic.patient_id AND ic.active = true " +
                     "WHERE p.active = true";

        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();

        StringBuilder report = new StringBuilder();
        report.append("ID,MRN,SSN,FirstName,LastName,DOB,PhoneHome,PhoneMobile,Email,Address,City,State,Zip,Insurance,MemberID")
              .append(System.lineSeparator());
        for (Object[] row : results) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.length; i++) {
                if (i > 0) line.append(",");
                String value = row[i] != null ? row[i].toString().replace(",", ";") : "";
                line.append(i == SSN_COLUMN_INDEX ? maskSsn(value) : value);
            }
            report.append(line).append(System.lineSeparator());
        }

        log.info("Generated patient roster with {} rows", results.size());
        return report.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generateEncounterSummary(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = "SELECT e.id, e.encounter_number, e.encounter_date_time, e.encounter_type, e.status, " +
                     "p.mrn, p.first_name, p.last_name, p.date_of_birth, " +
                     "pr.first_name as provider_first, pr.last_name as provider_last " +
                     "FROM encounters e " +
                     "JOIN patients p ON e.patient_id = p.id " +
                     "LEFT JOIN providers pr ON e.attending_provider_id = pr.id " +
                     "WHERE e.encounter_date_time BETWEEN ?1 AND ?2";

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, startDate);
        query.setParameter(2, endDate);
        List<Object[]> results = query.getResultList();

        StringBuilder report = new StringBuilder();
        appendLine(report, "ENCOUNTER SUMMARY REPORT");
        appendLine(report, "========================");
        appendLine(report, "Date Range: " + startDate + " to " + endDate);
        appendLine(report, "Generated: " + LocalDateTime.now());
        appendLine(report, "Total Encounters: " + results.size());
        appendLine(report, "");

        for (Object[] row : results) {
            appendLine(report, "Encounter: " + row[1]);
            appendLine(report, "  Date: " + row[2]);
            appendLine(report, "  Type: " + row[3] + " | Status: " + row[4]);
            appendLine(report, "  Patient: " + row[6] + " " + row[7] + " (MRN: " + row[5] + ")");
            appendLine(report, "  DOB: " + row[8]);
            appendLine(report, "  Provider: " + row[9] + " " + row[10]);
            appendLine(report, "");
        }

        log.info("Generated encounter summary with {} encounters", results.size());
        return report.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generateDailyReport() {
        return generatePatientRoster();
    }

    private static void appendLine(StringBuilder report, String line) {
        report.append(line).append(System.lineSeparator());
    }

    private static String maskSsn(String ssn) {
        if (ssn.isEmpty()) {
            return ssn;
        }
        String digits = ssn.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "XXX-XX-XXXX";
        }
        return "XXX-XX-" + digits.substring(digits.length() - 4);
    }
}
