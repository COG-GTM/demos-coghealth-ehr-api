package com.medchart.ehr.repository;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, Long> {

    @EntityGraph(attributePaths = {"patient", "attendingProvider"})
    Optional<Encounter> findByEncounterNumber(String encounterNumber);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    List<Encounter> findByPatientId(Long patientId);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    Page<Encounter> findByPatientId(Long patientId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    List<Encounter> findByPatientIdAndAttendingProviderId(Long patientId, Long providerId);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    Page<Encounter> findByPatientIdAndAttendingProviderId(Long patientId, Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    List<Encounter> findByAttendingProviderId(Long providerId);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    List<Encounter> findByStatus(EncounterStatus status);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    List<Encounter> findByStatusAndAttendingProviderId(EncounterStatus status, Long providerId);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    @Query("SELECT e FROM Encounter e WHERE e.encounterDateTime BETWEEN :startDate AND :endDate")
    List<Encounter> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    @Query("SELECT e FROM Encounter e WHERE e.attendingProvider.id = :providerId AND e.encounterDateTime BETWEEN :startDate AND :endDate")
    List<Encounter> findByDateRangeAndProvider(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("providerId") Long providerId);

    @EntityGraph(attributePaths = {"patient", "attendingProvider", "diagnoses"})
    @Query("SELECT e FROM Encounter e WHERE e.attendingProvider.id = :providerId AND e.encounterDateTime >= :startOfDay AND e.encounterDateTime < :endOfDay AND e.status IN ('SCHEDULED', 'CHECKED_IN', 'IN_PROGRESS')")
    List<Encounter> findTodaysSchedule(@Param("providerId") Long providerId, @Param("startOfDay") LocalDateTime startOfDay, @Param("endOfDay") LocalDateTime endOfDay);

    @Query("SELECT COUNT(e) FROM Encounter e WHERE e.patient.id = :patientId")
    long countByPatientId(@Param("patientId") Long patientId);

    @Query("SELECT e FROM Encounter e JOIN FETCH e.patient JOIN FETCH e.attendingProvider WHERE e.id = :id")
    Optional<Encounter> findByIdWithDetails(@Param("id") Long id);
}
