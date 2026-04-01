package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class LegacyPatientLookup {

    @Autowired
    private EntityManager entityManager;

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "Legacy lookup patient by MRN")
    public Patient findPatientByMrn(String mrn) {
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT * FROM patients WHERE mrn = ?1", Patient.class);
            query.setParameter(1, mrn);
            return (Patient) query.getSingleResult();
        } catch (Exception e) {
            log.warn("Patient not found for MRN: " + mrn);
            return null;
        }
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Legacy search patients by last name")
    public List<Patient> findPatientsByLastName(String lastName) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE last_name ILIKE ?1", Patient.class);
        query.setParameter(1, "%" + lastName + "%");
        return query.getResultList();
    }

    /**
     * @deprecated SSN-based patient lookup is disabled for HIPAA compliance.
     * Use findPatientByMrn() or findPatientsByLastName() instead.
     */
    @Deprecated
    public Patient findPatientBySsn(String ssn) {
        throw new UnsupportedOperationException(
            "SSN-based patient lookup is disabled for HIPAA compliance. " +
            "Use MRN or name-based search instead.");
    }

    @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "Legacy get patient demographics")
    public Map<String, Object> getPatientDemographics(Long patientId) {
        Query query = entityManager.createNativeQuery(
            "SELECT id, mrn, first_name, last_name, date_of_birth, " +
            "phone_home, phone_mobile, email, street1, city, state, zip_code " +
            "FROM patients WHERE id = ?1");
        query.setParameter(1, patientId);
        
        Object[] result = (Object[]) query.getSingleResult();
        Map<String, Object> demographics = new HashMap<>();
        demographics.put("id", result[0]);
        demographics.put("mrn", result[1]);
        demographics.put("firstName", result[2]);
        demographics.put("lastName", result[3]);
        demographics.put("dateOfBirth", result[4]);
        demographics.put("phoneHome", result[5]);
        demographics.put("phoneMobile", result[6]);
        demographics.put("email", result[7]);
        demographics.put("street", result[8]);
        demographics.put("city", result[9]);
        demographics.put("state", result[10]);
        demographics.put("zipCode", result[11]);
        
        return demographics;
    }

    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Patient", description = "Legacy raw patient search")
    public List<Object[]> searchPatientsRaw(String searchTerm) {
        String sql = "SELECT id, mrn, first_name, last_name, date_of_birth " +
                     "FROM patients WHERE " +
                     "first_name ILIKE ?1 OR last_name ILIKE ?1 OR mrn ILIKE ?1";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, "%" + searchTerm + "%");
        return query.getResultList();
    }
}
