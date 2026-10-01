package com.medchart.ehr.repository;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Encounter} entities.
 *
 * Provides queries by patient, provider, date range, status, and
 * a provider's daily schedule with eager-fetched associations.
 */
@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    Optional<Encounter> findByEncounterNumber(String encounterNumber);

    List<Encounter> findByPatientId(Long patientId);

    Page<Encounter> findByPatientId(Long patientId, Pageable pageable);

    List<Encounter> findByAttendingProviderId(Long providerId);

    List<Encounter> findByStatus(EncounterStatus status);

    @Query("SELECT e FROM Encounter e WHERE e.encounterDateTime BETWEEN :startDate AND :endDate")
    List<Encounter> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT e FROM Encounter e WHERE e.attendingProvider.id = :providerId AND e.encounterDateTime >= :startOfDay AND e.encounterDateTime < :endOfDay AND e.status IN ('SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS')")
    List<Encounter> findTodaysSchedule(@Param("providerId") Long providerId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(e) FROM Encounter e WHERE e.patient.id = :patientId")
    long countByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient JOIN FETCH e.attendingProvider WHERE e.id = :id")
    Optional<Encounter> findByIdWithDetails(@Param("id") Long id);
}
