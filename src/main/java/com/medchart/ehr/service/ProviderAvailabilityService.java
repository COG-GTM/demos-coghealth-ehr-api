package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.BlockType;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderAvailability;
import com.medchart.ehr.dto.ProviderAvailabilityDTO;
import com.medchart.ehr.repository.ProviderAvailabilityRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProviderAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(ProviderAvailabilityService.class);

    private final ProviderAvailabilityRepository availabilityRepository;
    private final ProviderRepository providerRepository;

    public ProviderAvailabilityService(ProviderAvailabilityRepository availabilityRepository,
                                       ProviderRepository providerRepository) {
        this.availabilityRepository = availabilityRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public List<ProviderAvailability> findByProvider(Long providerId) {
        return availabilityRepository.findByProviderIdAndActiveTrue(providerId);
    }

    @Transactional(readOnly = true)
    public List<ProviderAvailability> findRecurring(Long providerId) {
        return availabilityRepository.findByProviderIdAndRecurringTrueAndActiveTrue(providerId);
    }

    @Transactional(readOnly = true)
    public List<ProviderAvailability> findByDate(Long providerId, LocalDate date) {
        return availabilityRepository.findByProviderIdAndSpecificDateAndActiveTrue(providerId, date);
    }

    @Transactional(readOnly = true)
    public List<ProviderAvailability> findByDateRange(Long providerId, LocalDate startDate, LocalDate endDate) {
        return availabilityRepository.findByProviderIdAndDateRange(providerId, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<ProviderAvailability> findEffectiveForDate(Long providerId, LocalDate date) {
        int dayOfWeek = date.getDayOfWeek().getValue() % 7;
        List<ProviderAvailability> recurring = availabilityRepository.findEffectiveRecurringForDate(
                providerId, dayOfWeek, date);
        List<ProviderAvailability> overrides = availabilityRepository.findByProviderIdAndSpecificDateAndActiveTrue(
                providerId, date);
        List<ProviderAvailability> result = new ArrayList<>(recurring);
        result.addAll(overrides);
        return result;
    }

    @Transactional(readOnly = true)
    public Optional<ProviderAvailability> findById(Long id) {
        return availabilityRepository.findById(id);
    }

    public ProviderAvailability create(Long providerId, ProviderAvailabilityDTO dto) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        validateAvailability(dto);

        ProviderAvailability availability = ProviderAvailability.builder()
                .provider(provider)
                .blockType(dto.getBlockType())
                .dayOfWeek(dto.getDayOfWeek())
                .specificDate(dto.getSpecificDate())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .slotDuration(dto.getSlotDuration() != null ? dto.getSlotDuration() : 30)
                .visitTypesAllowed(dto.getVisitTypesAllowed())
                .recurring(dto.getRecurring() != null ? dto.getRecurring() : false)
                .recurrencePattern(dto.getRecurrencePattern())
                .effectiveFrom(dto.getEffectiveFrom())
                .effectiveUntil(dto.getEffectiveUntil())
                .overrideReason(dto.getOverrideReason())
                .notes(dto.getNotes())
                .active(true)
                .build();

        log.info("Creating availability block for provider {}: {} on {}",
                providerId, dto.getBlockType(),
                dto.getRecurring() ? "day " + dto.getDayOfWeek() : dto.getSpecificDate());

        return availabilityRepository.save(availability);
    }

    public ProviderAvailability update(Long id, ProviderAvailabilityDTO dto) {
        ProviderAvailability existing = availabilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Availability block not found: " + id));

        validateAvailability(dto);

        existing.setBlockType(dto.getBlockType());
        existing.setDayOfWeek(dto.getDayOfWeek());
        existing.setSpecificDate(dto.getSpecificDate());
        existing.setStartTime(dto.getStartTime());
        existing.setEndTime(dto.getEndTime());
        existing.setSlotDuration(dto.getSlotDuration() != null ? dto.getSlotDuration() : 30);
        existing.setVisitTypesAllowed(dto.getVisitTypesAllowed());
        existing.setRecurring(dto.getRecurring() != null ? dto.getRecurring() : false);
        existing.setRecurrencePattern(dto.getRecurrencePattern());
        existing.setEffectiveFrom(dto.getEffectiveFrom());
        existing.setEffectiveUntil(dto.getEffectiveUntil());
        existing.setOverrideReason(dto.getOverrideReason());
        existing.setNotes(dto.getNotes());

        log.info("Updating availability block {}", id);

        return availabilityRepository.save(existing);
    }

    public void deactivate(Long id) {
        availabilityRepository.findById(id).ifPresent(availability -> {
            availability.setActive(false);
            availabilityRepository.save(availability);
            log.info("Deactivated availability block {}", id);
        });
    }

    public void delete(Long id) {
        availabilityRepository.deleteById(id);
        log.info("Deleted availability block {}", id);
    }

    private void validateAvailability(ProviderAvailabilityDTO dto) {
        if (dto.getStartTime() != null && dto.getEndTime() != null
                && !dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        if (Boolean.TRUE.equals(dto.getRecurring()) && dto.getDayOfWeek() == null) {
            throw new IllegalArgumentException("Day of week is required for recurring blocks");
        }

        if (!Boolean.TRUE.equals(dto.getRecurring()) && dto.getSpecificDate() == null) {
            throw new IllegalArgumentException("Specific date is required for non-recurring blocks");
        }

        if (dto.getSlotDuration() != null) {
            int duration = dto.getSlotDuration();
            if (duration != 15 && duration != 30 && duration != 45 && duration != 60) {
                throw new IllegalArgumentException("Slot duration must be 15, 30, 45, or 60 minutes");
            }
        }

        if (dto.getDayOfWeek() != null && (dto.getDayOfWeek() < 0 || dto.getDayOfWeek() > 6)) {
            throw new IllegalArgumentException("Day of week must be between 0 (Sunday) and 6 (Saturday)");
        }
    }
}
