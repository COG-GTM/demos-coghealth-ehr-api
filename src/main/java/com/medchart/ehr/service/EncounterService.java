package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.repository.EncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    private final EncounterRepository encounterRepository;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findById(Long id) {
        return encounterRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findByIdWithDetails(Long id) {
        return encounterRepository.findByIdWithDetails(id);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter")
    public Optional<Encounter> findByIdForRequester(Long id, User requester) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(id)
                .filter(existing -> canAccess(existing, requester));
        if (requester != null) {
            log.info(
                    "User {} (provider {}) accessed encounter",
                    requester.getId(), requester.getProviderId());
        }
        return encounter;
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    public Optional<Encounter> findByEncounterNumber(String encounterNumber, User requester) {
        return encounterRepository.findByEncounterNumber(encounterNumber)
                .filter(encounter -> canAccess(encounter, requester));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "List patient encounters")
    public List<Encounter> findByPatientId(Long patientId, User requester) {
        if (isAdmin(requester)) {
            return encounterRepository.findByPatientId(patientId);
        }
        Long providerId = providerId(requester);
        if (providerId == null) {
            return List.of();
        }
        return encounterRepository.findByPatientIdAndAttendingProviderId(patientId, providerId);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Page patient encounters")
    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable, User requester) {
        if (isAdmin(requester)) {
            return encounterRepository.findByPatientId(patientId, pageable);
        }
        Long providerId = providerId(requester);
        if (providerId == null) {
            return Page.empty(pageable);
        }
        return encounterRepository.findByPatientIdAndAttendingProviderId(
                patientId, providerId, pageable);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "List provider encounters")
    public List<Encounter> findByProviderId(Long providerId, User requester) {
        requireProviderAccess(providerId, requester);
        return encounterRepository.findByAttendingProviderId(providerId);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View provider schedule")
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date, User requester) {
        requireProviderAccess(providerId, requester);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by date range")
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate, User requester) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        if (isAdmin(requester)) {
            return encounterRepository.findByDateRange(start, end);
        }
        Long providerId = providerId(requester);
        if (providerId == null) {
            return List.of();
        }
        return encounterRepository.findByDateRangeAndAttendingProviderId(start, end, providerId);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by status")
    public List<Encounter> findByStatus(EncounterStatus status, User requester) {
        if (isAdmin(requester)) {
            return encounterRepository.findByStatus(status);
        }
        Long providerId = providerId(requester);
        if (providerId == null) {
            return List.of();
        }
        return encounterRepository.findByStatusAndAttendingProviderId(status, providerId);
    }

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
    public Encounter create(Encounter encounter, User requester) {
        if (!isAdmin(requester)
                && (providerId(requester) == null
                || encounter.getAttendingProvider() == null
                || !providerId(requester).equals(encounter.getAttendingProvider().getId()))) {
            throw new AccessDeniedException("Cannot create an encounter for another provider");
        }

        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        return encounterRepository.save(encounter);
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Update encounter")
    public Optional<Encounter> update(Long id, Encounter encounter, User requester) {
        return encounterRepository.findById(id)
                .filter(existing -> canAccess(existing, requester))
                .map(existing -> {
                    if (!isAdmin(requester)
                            && (encounter.getAttendingProvider() == null
                            || providerId(requester) == null
                            || !providerId(requester).equals(encounter.getAttendingProvider().getId()))) {
                        throw new AccessDeniedException("Cannot assign an encounter to another provider");
                    }
                    encounter.setId(id);
                    return encounterRepository.save(encounter);
                });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Check in encounter")
    public boolean checkIn(Long encounterId, User requester) {
        return updateEncounter(encounterId, requester, encounter -> encounter.setStatus(EncounterStatus.CHECKED_IN));
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Start encounter")
    public boolean startEncounter(Long encounterId, User requester) {
        return updateEncounter(encounterId, requester, encounter -> encounter.setStatus(EncounterStatus.IN_PROGRESS));
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Complete encounter")
    public boolean completeEncounter(Long encounterId, String notes, User requester) {
        return updateEncounter(encounterId, requester, encounter -> {
            encounter.setStatus(EncounterStatus.COMPLETED);
            if (notes != null) {
                encounter.setNotes(notes);
            }
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Cancel encounter")
    public boolean cancelEncounter(Long encounterId, User requester) {
        return updateEncounter(encounterId, requester, encounter -> encounter.setStatus(EncounterStatus.CANCELLED));
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Mark encounter as no-show")
    public boolean markNoShow(Long encounterId, User requester) {
        return updateEncounter(encounterId, requester, encounter -> encounter.setStatus(EncounterStatus.NO_SHOW));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "Count patient encounters")
    public long getPatientEncounterCount(Long patientId) {
        return encounterRepository.countByPatientId(patientId);
    }

    private boolean updateEncounter(Long encounterId, User requester, Consumer<Encounter> update) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(encounterId)
                .filter(existing -> canAccess(existing, requester));
        encounter.ifPresent(existing -> {
            update.accept(existing);
            encounterRepository.save(existing);
        });
        return encounter.isPresent();
    }

    private boolean canAccess(Encounter encounter, User requester) {
        if (encounter == null || requester == null) {
            return false;
        }
        if (isAdmin(requester)) {
            return true;
        }
        return providerId(requester) != null
                && encounter.getAttendingProvider() != null
                && providerId(requester).equals(encounter.getAttendingProvider().getId());
    }

    private void requireProviderAccess(Long providerId, User requester) {
        if (!isAdmin(requester) && !providerId.equals(providerId(requester))) {
            throw new AccessDeniedException("Cannot access another provider's encounters");
        }
    }

    private boolean isAdmin(User requester) {
        return requester != null && requester.isAdmin();
    }

    private Long providerId(User requester) {
        return requester == null ? null : requester.getProviderId();
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
