package com.medchart.ehr.repository;

import com.medchart.ehr.domain.provider.BlockType;
import com.medchart.ehr.domain.provider.ProviderAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProviderAvailabilityRepository extends JpaRepository<ProviderAvailability, Long> {

    List<ProviderAvailability> findByProviderIdAndActiveTrue(Long providerId);

    List<ProviderAvailability> findByProviderIdAndRecurringTrueAndActiveTrue(Long providerId);

    List<ProviderAvailability> findByProviderIdAndSpecificDateAndActiveTrue(Long providerId, LocalDate specificDate);

    List<ProviderAvailability> findByProviderIdAndBlockTypeAndActiveTrue(Long providerId, BlockType blockType);

    @Query("SELECT pa FROM ProviderAvailability pa WHERE pa.provider.id = :providerId " +
           "AND pa.active = true " +
           "AND pa.recurring = true " +
           "AND pa.dayOfWeek = :dayOfWeek " +
           "AND (pa.effectiveFrom IS NULL OR pa.effectiveFrom <= :date) " +
           "AND (pa.effectiveUntil IS NULL OR pa.effectiveUntil >= :date)")
    List<ProviderAvailability> findEffectiveRecurringForDate(
            @Param("providerId") Long providerId,
            @Param("dayOfWeek") Integer dayOfWeek,
            @Param("date") LocalDate date);

    @Query("SELECT pa FROM ProviderAvailability pa WHERE pa.provider.id = :providerId " +
           "AND pa.active = true " +
           "AND ((pa.recurring = true AND (pa.effectiveFrom IS NULL OR pa.effectiveFrom <= :endDate) " +
           "AND (pa.effectiveUntil IS NULL OR pa.effectiveUntil >= :startDate)) " +
           "OR (pa.recurring = false AND pa.specificDate >= :startDate AND pa.specificDate <= :endDate))")
    List<ProviderAvailability> findByProviderIdAndDateRange(
            @Param("providerId") Long providerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
