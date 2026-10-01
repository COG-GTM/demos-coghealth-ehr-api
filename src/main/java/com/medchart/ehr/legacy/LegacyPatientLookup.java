package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LegacyPatientLookup {

    private static final Map<String, String> DEMOGRAPHIC_COLUMNS = demographicColumns();

    @Autowired
    private EntityManager entityManager;

    public Patient findPatientByMrn(String mrn) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE mrn = ?1", Patient.class);
        query.setParameter(1, mrn);
        Patient patient = firstOrNull(query);
        if (patient == null) {
            log.warn("Patient not found for the requested MRN");
        }
        return patient;
    }

    public List<Patient> findPatientsByLastName(String lastName) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE last_name ILIKE ?1", Patient.class);
        query.setParameter(1, "%" + lastName + "%");
        return query.getResultList();
    }

    public Patient findPatientBySsn(String ssn) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE ssn = ?1", Patient.class);
        query.setParameter(1, ssn);
        return firstOrNull(query);
    }

    public Map<String, Object> getPatientDemographics(Long patientId) {
        Query query = entityManager.createNativeQuery(
            "SELECT " + String.join(", ", DEMOGRAPHIC_COLUMNS.keySet()) + " FROM patients WHERE id = ?1");
        query.setParameter(1, patientId);

        Object[] result = firstOrNull(query);
        if (result == null) {
            return null;
        }

        Map<String, Object> demographics = new HashMap<>();
        int column = 0;
        for (String key : DEMOGRAPHIC_COLUMNS.values()) {
            demographics.put(key, result[column++]);
        }
        return demographics;
    }

    public List<Object[]> searchPatientsRaw(String searchTerm) {
        String sql = "SELECT id, mrn, first_name, last_name, date_of_birth " +
                     "FROM patients WHERE " +
                     "first_name ILIKE ?1 OR last_name ILIKE ?1 OR mrn ILIKE ?1";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, "%" + searchTerm + "%");
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private static <T> T firstOrNull(Query query) {
        List<T> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    private static Map<String, String> demographicColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id");
        columns.put("mrn", "mrn");
        columns.put("ssn", "ssn");
        columns.put("first_name", "firstName");
        columns.put("last_name", "lastName");
        columns.put("date_of_birth", "dateOfBirth");
        columns.put("phone_home", "phoneHome");
        columns.put("phone_mobile", "phoneMobile");
        columns.put("email", "email");
        columns.put("street1", "street");
        columns.put("city", "city");
        columns.put("state", "state");
        columns.put("zip_code", "zipCode");
        return Collections.unmodifiableMap(columns);
    }
}
