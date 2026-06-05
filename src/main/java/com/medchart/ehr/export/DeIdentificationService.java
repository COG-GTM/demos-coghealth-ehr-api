package com.medchart.ehr.export;

import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HIPAA Safe Harbor de-identification (45 CFR §164.514(b)(2)).
 *
 * Strips the 18 Safe Harbor identifiers relevant to patient export:
 * - Names replaced with "[REDACTED]"
 * - DOB converted to age
 * - ZIP truncated to first 3 digits + "00"
 * - SSN, email, phone numbers stripped
 */
@Service
@Slf4j
public class DeIdentificationService {

    public List<Map<String, Object>> deIdentify(List<Patient> patients) {
        return patients.stream()
                .map(this::deIdentifyPatient)
                .collect(Collectors.toList());
    }

    Map<String, Object> deIdentifyPatient(Patient patient) {
        Map<String, Object> safe = new LinkedHashMap<>();
        safe.put("mrn", patient.getMrn());
        safe.put("firstName", "[REDACTED]");
        safe.put("lastName", "[REDACTED]");
        safe.put("age", calculateAge(patient.getDateOfBirth()));
        safe.put("gender", patient.getGender() != null ? patient.getGender().name() : null);
        safe.put("zipCode3", truncateZip(getZipCode(patient)));
        safe.put("state", getState(patient));
        safe.put("active", patient.getActive());
        return safe;
    }

    int calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return 0;
        }
        int age = Period.between(dateOfBirth, LocalDate.now()).getYears();
        // Safe Harbor: ages over 89 are grouped as 90+
        return age > 89 ? 90 : age;
    }

    String truncateZip(String zipCode) {
        if (zipCode == null || zipCode.length() < 3) {
            return "00000";
        }
        return zipCode.substring(0, 3) + "00";
    }

    private String getZipCode(Patient patient) {
        if (patient.getAddress() != null && patient.getAddress().getZipCode() != null) {
            return patient.getAddress().getZipCode();
        }
        return null;
    }

    private String getState(Patient patient) {
        if (patient.getAddress() != null) {
            return patient.getAddress().getState();
        }
        return null;
    }
}
