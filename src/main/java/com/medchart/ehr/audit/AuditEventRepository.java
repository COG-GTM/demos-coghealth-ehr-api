package com.medchart.ehr.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for querying HIPAA audit trail records.
 *
 * Provides methods for retrieving audit events by patient, user, date range,
 * and action type to support compliance reporting and access reviews.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByPatientId(Long patientId, Pageable pageable);

    Page<AuditEvent> findByUserId(String userId, Pageable pageable);

    List<AuditEvent> findByPatientIdAndTimestampBetween(
            Long patientId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT a FROM AuditEvent a WHERE a.patientId = :patientId " +
           "AND a.action = :action ORDER BY a.timestamp DESC")
    List<AuditEvent> findPatientAccessByAction(Long patientId, AuditAction action);

    @Query("SELECT DISTINCT a.userId FROM AuditEvent a WHERE a.patientId = :patientId")
    List<String> findUsersWhoAccessedPatient(Long patientId);
}
