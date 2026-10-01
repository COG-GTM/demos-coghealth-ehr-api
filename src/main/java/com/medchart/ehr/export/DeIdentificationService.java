package com.medchart.ehr.export;

import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HIPAA Safe Harbor de-identification (45 CFR §164.514(b)(2)).
 *
 * Strips the Safe Harbor identifiers relevant to patient export:
 * - Names replaced with "[REDACTED]"
 * - Medical record number replaced with a random research id
 * - DOB converted to age (ages over 89 grouped as 90)
 * - ZIP truncated to first 3 digits + "00"
 * - SSN, email, phone numbers omitted entirely
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
        // Safe Harbor identifier #12 (medical record number) must be removed.
        // Use a non-reversible random research id so rows remain distinguishable.
        safe.put("researchId", UUID.randomUUID().toString());
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
