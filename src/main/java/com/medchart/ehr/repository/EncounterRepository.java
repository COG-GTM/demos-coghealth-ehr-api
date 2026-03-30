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

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.encounterNumber = :encounterNumber")
    Optional<Encounter> findByEncounterNumber(@Param("encounterNumber") String encounterNumber);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.patient.id = :patientId")
    List<Encounter> findByPatientId(@Param("patientId") Long patientId);

    @Query(value = "SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.patient.id = :patientId",
           countQuery = "SELECT COUNT(e) FROM Encounter e WHERE e.patient.id = :patientId")
    Page<Encounter> findByPatientId(@Param("patientId") Long patientId, Pageable pageable);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.attendingProvider.id = :providerId")
    List<Encounter> findByAttendingProviderId(@Param("providerId") Long providerId);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.status = :status")
    List<Encounter> findByStatus(@Param("status") EncounterStatus status);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.encounterDateTime BETWEEN :startDate AND :endDate")
    List<Encounter> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient LEFT JOIN FETCH e.attendingProvider WHERE e.attendingProvider.id = :providerId AND e.encounterDateTime >= :startOfDay AND e.encounterDateTime < :endOfDay AND e.status IN ('SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS')")
    List<Encounter> findTodaysSchedule(@Param("providerId") Long providerId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(e) FROM Encounter e WHERE e.patient.id = :patientId")
    long countByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient JOIN FETCH e.attendingProvider WHERE e.id = :id")
    Optional<Encounter> findByIdWithDetails(@Param("id") Long id);
}
