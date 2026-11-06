package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.patient.Patient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LegacyPatientLookup {

    private static final String LOOKUP_METRIC = "legacy.patient.lookup";

    private final EntityManager entityManager;
    private final MeterRegistry meterRegistry;

    @Autowired
    public LegacyPatientLookup(EntityManager entityManager, MeterRegistry meterRegistry) {
        this.entityManager = entityManager;
        this.meterRegistry = meterRegistry;
    }

    public Patient findPatientByMrn(String mrn) {
        return findSingle("mrn", "SELECT * FROM patients WHERE mrn = ?1", mrn);
    }

    public List<Patient> findPatientsByLastName(String lastName) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE last_name ILIKE ?1", Patient.class);
        query.setParameter(1, "%" + lastName + "%");
        return query.getResultList();
    }

    public Patient findPatientBySsn(String ssn) {
        return findSingle("ssn", "SELECT * FROM patients WHERE ssn = ?1", ssn);
    }

    private Patient findSingle(String lookupType, String sql, String value) {
        try {
            Query query = entityManager.createNativeQuery(sql, Patient.class);
            query.setParameter(1, value);
            Patient patient = (Patient) query.getSingleResult();
            count(lookupType, "found");
            return patient;
        } catch (NoResultException e) {
            count(lookupType, "not_found");
            log.debug("No patient matched legacy lookup by {}", lookupType);
            return null;
        } catch (RuntimeException e) {
            count(lookupType, "error");
            log.error("Legacy patient lookup by {} failed", lookupType, e);
            throw new LegacyLookupException(lookupType, e);
        }
    }

    private void count(String lookupType, String outcome) {
        meterRegistry.counter(LOOKUP_METRIC, "lookup", lookupType, "outcome", outcome).increment();
    }

    public Map<String, Object> getPatientDemographics(Long patientId) {
        Query query = entityManager.createNativeQuery(
            "SELECT id, mrn, ssn, first_name, last_name, date_of_birth, " +
            "phone_home, phone_mobile, email, street1, city, state, zip_code " +
            "FROM patients WHERE id = ?1");
        query.setParameter(1, patientId);
        
        Object[] result = (Object[]) query.getSingleResult();
        Map<String, Object> demographics = new HashMap<>();
        demographics.put("id", result[0]);
        demographics.put("mrn", result[1]);
        demographics.put("ssn", result[2]);
        demographics.put("firstName", result[3]);
        demographics.put("lastName", result[4]);
        demographics.put("dateOfBirth", result[5]);
        demographics.put("phoneHome", result[6]);
        demographics.put("phoneMobile", result[7]);
        demographics.put("email", result[8]);
        demographics.put("street", result[9]);
        demographics.put("city", result[10]);
        demographics.put("state", result[11]);
        demographics.put("zipCode", result[12]);
        
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
}
