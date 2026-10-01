package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.Gender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FHIR R4 Patient resource mapper.
 * 
 * PATTERN: FHIR Resource Mapping
 * - Map internal domain objects to FHIR R4 resources
 * - Handle identifier systems (MRN, SSN, etc.)
 * - Support both JSON and XML serialization
 * - Validate required FHIR elements
 * 
 * Reference: https://www.hl7.org/fhir/patient.html
 */
@Component
@Slf4j
public class FhirPatientMapper {

    private static final String FHIR_DATE_FORMAT = "yyyy-MM-dd";
    private static final String MRN_SYSTEM = "http://hospital.example.org/mrn";
    private static final String SSN_SYSTEM = "http://hl7.org/fhir/sid/us-ssn";

    /**
     * PATTERN: Convert internal Patient to FHIR Patient resource
     * 
     * Maps all relevant fields to FHIR R4 Patient structure.
     * Returns a Map that can be serialized to JSON.
     */
    public Map<String, Object> toFhirResource(Patient patient) {
        Map<String, Object> fhirPatient = new HashMap<>();
        
        // Resource metadata
        fhirPatient.put("resourceType", "Patient");
        fhirPatient.put("id", patient.getId().toString());
        
        // Identifiers (MRN, SSN)
        fhirPatient.put("identifier", buildIdentifiers(patient));
        
        // Name
        fhirPatient.put("name", List.of(buildName(patient)));
        
        // Gender
        fhirPatient.put("gender", mapGender(patient.getGender()));
        
        // Birth date
        if (patient.getDateOfBirth() != null) {
            fhirPatient.put("birthDate", patient.getDateOfBirth().format(DateTimeFormatter.ofPattern(FHIR_DATE_FORMAT)));
        }
        
        // Contact info
        fhirPatient.put("telecom", buildTelecom(patient));
        
        // Address
        if (patient.getAddress() != null) {
            fhirPatient.put("address", List.of(buildAddress(patient)));
        }
        
        // Active status
        fhirPatient.put("active", Boolean.TRUE.equals(patient.getActive()));
        
        // Deceased
        if (Boolean.TRUE.equals(patient.getDeceased())) {
            fhirPatient.put("deceasedBoolean", true);
            if (patient.getDeceasedDate() != null) {
                fhirPatient.put("deceasedDateTime", patient.getDeceasedDate().toString());
            }
        }
        
        // Marital status
        if (patient.getMaritalStatus() != null) {
            fhirPatient.put("maritalStatus", buildMaritalStatus(patient));
        }
        
        log.debug("Mapped patient {} to FHIR resource", patient.getMrn());
        return fhirPatient;
    }

    /**
     * PATTERN: Convert FHIR Patient resource to internal Patient
     */
    public Patient fromFhirResource(Map<String, Object> fhirPatient) {
        Patient patient = new Patient();
        
        // Extract identifiers
        List<Map<String, Object>> identifiers = (List<Map<String, Object>>) fhirPatient.get("identifier");
        if (identifiers != null) {
            for (Map<String, Object> identifier : identifiers) {
                String system = (String) identifier.get("system");
                String value = (String) identifier.get("value");
                
                if (MRN_SYSTEM.equals(system)) {
                    patient.setMrn(value);
                } else if (SSN_SYSTEM.equals(system)) {
                    patient.setSsn(value);
                }
            }
        }
        
        // Extract name
        List<Map<String, Object>> names = (List<Map<String, Object>>) fhirPatient.get("name");
        if (names != null && !names.isEmpty()) {
            Map<String, Object> name = names.get(0);
            patient.setLastName((String) name.get("family"));
            List<String> given = (List<String>) name.get("given");
            if (given != null && !given.isEmpty()) {
                patient.setFirstName(given.get(0));
                if (given.size() > 1) {
                    patient.setMiddleName(given.get(1));
                }
            }
        }
        
        // Extract gender
        String gender = (String) fhirPatient.get("gender");
        if (gender != null) {
            patient.setGender(mapFhirGender(gender));
        }
        
        // Extract birth date
        String birthDate = (String) fhirPatient.get("birthDate");
        if (birthDate != null) {
            patient.setDateOfBirth(LocalDate.parse(birthDate));
        }
        
        // Active status
        Boolean active = (Boolean) fhirPatient.get("active");
        patient.setActive(active != null ? active : true);
        
        log.debug("Mapped FHIR resource to patient {}", patient.getMrn());
        return patient;
    }

    private List<Map<String, Object>> buildIdentifiers(Patient patient) {
        return List.of(
            Map.of(
                "system", MRN_SYSTEM,
                "value", patient.getMrn(),
                "use", "official"
            )
        );
    }

    private Map<String, Object> buildName(Patient patient) {
        Map<String, Object> name = new HashMap<>();
        name.put("use", "official");
        name.put("family", patient.getLastName());
        
        if (patient.getMiddleName() != null) {
            name.put("given", List.of(patient.getFirstName(), patient.getMiddleName()));
        } else {
            name.put("given", List.of(patient.getFirstName()));
        }
        
        return name;
    }

    private String mapGender(Gender gender) {
        if (gender == null) return "unknown";
        switch (gender) {
            case MALE: return "male";
            case FEMALE: return "female";
            case OTHER: return "other";
            default: return "unknown";
        }
    }

    private Gender mapFhirGender(String fhirGender) {
        switch (fhirGender.toLowerCase()) {
            case "male": return Gender.MALE;
            case "female": return Gender.FEMALE;
            case "other": return Gender.OTHER;
            default: return Gender.OTHER;
        }
    }

    private List<Map<String, Object>> buildTelecom(Patient patient) {
        java.util.ArrayList<Map<String, Object>> telecom = new java.util.ArrayList<>();
        
        if (patient.getPhoneHome() != null) {
            telecom.add(Map.of("system", "phone", "value", patient.getPhoneHome(), "use", "home"));
        }
        if (patient.getPhoneMobile() != null) {
            telecom.add(Map.of("system", "phone", "value", patient.getPhoneMobile(), "use", "mobile"));
        }
        if (patient.getEmail() != null) {
            telecom.add(Map.of("system", "email", "value", patient.getEmail()));
        }
        
        return telecom;
    }

    private Map<String, Object> buildAddress(Patient patient) {
        var addr = patient.getAddress();
        Map<String, Object> address = new HashMap<>();
        address.put("use", "home");
        
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        if (addr.getStreet1() != null) lines.add(addr.getStreet1());
        if (addr.getStreet2() != null) lines.add(addr.getStreet2());
        address.put("line", lines);
        
        address.put("city", addr.getCity());
        address.put("state", addr.getState());
        address.put("postalCode", addr.getZipCode());
        address.put("country", "US");
        
        return address;
    }

    private Map<String, Object> buildMaritalStatus(Patient patient) {
        String code;
        switch (patient.getMaritalStatus()) {
            case SINGLE: code = "S"; break;
            case MARRIED: code = "M"; break;
            case DIVORCED: code = "D"; break;
            case WIDOWED: code = "W"; break;
            default: code = "UNK"; break;
        }
        
        Map<String, Object> coding = new java.util.HashMap<>();
        coding.put("system", "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus");
        coding.put("code", code);
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("coding", java.util.Collections.singletonList(coding));
        return result;
    }
}
