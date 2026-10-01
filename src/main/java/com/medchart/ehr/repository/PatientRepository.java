package com.medchart.ehr.repository;

import com.medchart.ehr.domain.patient.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Patient} entities.
 *
 * Provides lookup by MRN, SSN, name, and date of birth, as well as
 * free-text search across patient demographics.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByMrn(String mrn);

    Optional<Patient> findBySsn(String ssn);

    Page<Patient> findByLastNameContainingIgnoreCase(String lastName, Pageable pageable);

    @Query("SELECT p FROM Patient p WHERE " +
           "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "p.mrn LIKE CONCAT('%', :searchTerm, '%')")
    Page<Patient> searchPatients(String searchTerm, Pageable pageable);

    List<Patient> findByDateOfBirth(LocalDate dateOfBirth);

    @Query("SELECT p FROM Patient p WHERE p.lastName = :lastName AND p.dateOfBirth = :dob")
    List<Patient> findByLastNameAndDob(String lastName, LocalDate dob);

    List<Patient> findByActiveTrue();

    @Query("SELECT COUNT(p) FROM Patient p WHERE p.active = true")
    long countActivePatients();
}
