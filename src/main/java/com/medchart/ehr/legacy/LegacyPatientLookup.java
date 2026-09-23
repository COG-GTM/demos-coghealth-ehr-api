package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.patient.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy native-SQL patient lookups.
 *
 * Every method here reads PHI, so each access is recorded through
 * {@link PatientAccessLogger} (the same audit trail the annotated
 * PatientService methods write to). SSN values are never logged.
 */
@Service
@Slf4j
public class LegacyPatientLookup {

    private static final String RESOURCE_TYPE = "Patient";
    private static final String UNKNOWN_USER = "system";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PatientAccessLogger patientAccessLogger;

    public Patient findPatientByMrn(String mrn) {
        Patient patient;
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT * FROM patients WHERE mrn = ?1", Patient.class);
            query.setParameter(1, mrn);
            patient = (Patient) query.getSingleResult();
        } catch (Exception e) {
            log.warn("Patient not found for MRN: " + mrn);
            return null;
        }
        logAccess(patient, AuditAction.READ, "Legacy lookup by MRN");
        return patient;
    }

    public List<Patient> findPatientsByLastName(String lastName) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE last_name ILIKE ?1", Patient.class);
        query.setParameter(1, "%" + lastName + "%");
        @SuppressWarnings("unchecked")
        List<Patient> patients = query.getResultList();
        logBulkAccess(patients.size(), "Legacy search by last name");
        return patients;
    }

    public Patient findPatientBySsn(String ssn) {
        Query query = entityManager.createNativeQuery(
            "SELECT * FROM patients WHERE ssn = ?1", Patient.class);
        query.setParameter(1, ssn);
        Patient patient;
        try {
            patient = (Patient) query.getSingleResult();
        } catch (Exception e) {
            patientAccessLogger.logFailedAccess(currentUser(), currentUser(), null,
                AuditAction.READ, RESOURCE_TYPE, "Legacy lookup by SSN matched no patient",
                clientIpAddress());
            return null;
        }
        logAccess(patient, AuditAction.READ, "Legacy lookup by SSN");
        return patient;
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

        patientAccessLogger.logAccess(currentUser(), currentUser(), patientId,
            result[1] == null ? null : String.valueOf(result[1]), AuditAction.READ,
            RESOURCE_TYPE, "Legacy demographics read (includes SSN)",
            clientIpAddress(), sessionId());

        return demographics;
    }

    public List<Object[]> searchPatientsRaw(String searchTerm) {
        String sql = "SELECT id, mrn, first_name, last_name, date_of_birth " +
                     "FROM patients WHERE " +
                     "first_name ILIKE ?1 OR last_name ILIKE ?1 OR mrn ILIKE ?1";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, "%" + searchTerm + "%");
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        logBulkAccess(rows.size(), "Legacy raw patient search");
        return rows;
    }

    private void logAccess(Patient patient, AuditAction action, String reason) {
        patientAccessLogger.logAccess(currentUser(), currentUser(), patient.getId(),
            patient.getMrn(), action, RESOURCE_TYPE, reason, clientIpAddress(), sessionId());
    }

    private void logBulkAccess(int recordCount, String reason) {
        patientAccessLogger.logBulkAccess(currentUser(), currentUser(), AuditAction.SEARCH,
            RESOURCE_TYPE, recordCount, reason, clientIpAddress());
    }

    private String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return UNKNOWN_USER;
    }

    private String clientIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String sessionId() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? null : attributes.getSessionId();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = requestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private ServletRequestAttributes requestAttributes() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes
            ? (ServletRequestAttributes) RequestContextHolder.getRequestAttributes()
            : null;
    }
}
