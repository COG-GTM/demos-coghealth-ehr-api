package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.patient.Patient;
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

    private static final String LEGACY_LOOKUP_RESOURCE = "Patient";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PatientAccessLogger patientAccessLogger;

    public Patient findPatientByMrn(String mrn) {
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT * FROM patients WHERE mrn = ?1", Patient.class);
            query.setParameter(1, mrn);
            return (Patient) query.getSingleResult();
        } catch (NoResultException e) {
            recordFailedLookup("Patient not found for MRN " + maskMrn(mrn));
            return null;
        } catch (RuntimeException e) {
            recordFailedLookup("Patient lookup failed for MRN " + maskMrn(mrn)
                + " - " + e.getClass().getSimpleName());
            log.error("Legacy patient lookup by MRN failed", e);
            throw e;
        }
    }

    private void recordFailedLookup(String reason) {
        patientAccessLogger.logFailedAccess(
            null,
            "LEGACY_LOOKUP",
            null,
            AuditAction.READ,
            LEGACY_LOOKUP_RESOURCE,
            reason,
            null);
    }

    /**
     * Masks a patient identifier so it can appear in audit records without
     * exposing PHI: everything but the last four characters is redacted.
     */
    static String maskMrn(String mrn) {
        if (mrn == null || mrn.isEmpty()) {
            return "[absent]";
        }
        if (mrn.length() <= 4) {
            return "****";
        }
        return "****" + mrn.substring(mrn.length() - 4);
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
        try {
            return (Patient) query.getSingleResult();
        } catch (Exception e) {
            return null;
        }
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
