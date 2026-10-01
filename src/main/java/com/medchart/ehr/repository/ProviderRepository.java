package com.medchart.ehr.repository;

import com.medchart.ehr.domain.provider.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Provider} entities.
 *
 * Supports lookup by NPI, department, specialty, and name search,
 * as well as distinct department and specialty listings.
 */
@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByNpi(String npi);

    List<Provider> findByActiveTrue();

    List<Provider> findByDepartment(String department);

    List<Provider> findBySpecialty(String specialty);

    @Query("SELECT DISTINCT p.department FROM Provider p WHERE p.active = true ORDER BY p.department")
    List<String> findAllDepartments();

    @Query("SELECT DISTINCT p.specialty FROM Provider p WHERE p.active = true ORDER BY p.specialty")
    List<String> findAllSpecialties();

    List<Provider> findByLastNameContainingIgnoreCase(String lastName);
}
