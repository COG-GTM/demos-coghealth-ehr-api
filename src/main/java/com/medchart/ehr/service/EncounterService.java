package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.dto.EncounterResponseDTO;
import com.medchart.ehr.repository.EncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    
    private final EncounterRepository encounterRepository;
    static final long MAX_DATE_RANGE_DAYS = 31;
    static final int MAX_NOTES_LENGTH = 1000;

    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Encounter> findById(Long id) {
        return encounterRepository.findById(id);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> findByIdWithDetails(Long id) {
        return encounterRepository.findByIdWithDetails(id).map(EncounterResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter for provider",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> findByIdForProvider(Long id, Long providerId) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(id);
        encounter.ifPresent(enc -> log.info("Provider {} accessed encounter {}", providerId, enc.getEncounterNumber()));
        return encounter.map(EncounterResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    public Optional<EncounterResponseDTO> findByEncounterNumber(String encounterNumber) {
        return encounterRepository.findByEncounterNumber(encounterNumber).map(EncounterResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters")
    public List<EncounterResponseDTO> findByPatientId(Long patientId) {
        return toResponses(encounterRepository.findByPatientId(patientId));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters (paged)")
    public Page<EncounterResponseDTO> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable).map(EncounterResponseDTO::from);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "List provider encounters",
            patientIdFromArgs = false)
    public List<EncounterResponseDTO> findByProviderId(Long providerId) {
        return toResponses(encounterRepository.findByAttendingProviderId(providerId));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "View provider schedule",
            patientIdFromArgs = false)
    public List<EncounterResponseDTO> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return toResponses(encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by date range")
    public List<EncounterResponseDTO> findByDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) >= MAX_DATE_RANGE_DAYS) {
            throw new IllegalArgumentException("Date range must not exceed " + MAX_DATE_RANGE_DAYS + " days");
        }
        return toResponses(encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Encounter", description = "Search encounters by status")
    public List<EncounterResponseDTO> findByStatus(EncounterStatus status) {
        return toResponses(encounterRepository.findByStatus(status));
    }

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Encounter", description = "Create encounter")
    public EncounterResponseDTO create(Encounter encounter) {
        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return EncounterResponseDTO.from(saved);
    }

    /**
     * Applies the editable scheduling/clinical fields from {@code changes} onto the stored encounter.
     * Identity, patient, provider, status, diagnoses and audit metadata are never taken from the request,
     * so a round-tripped {@link EncounterResponseDTO} cannot null them out. Null fields are left unchanged.
     * A supplied {@code version} that no longer matches the stored one is rejected as a stale edit.
     */
    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Update encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> update(Long id, Encounter changes) {
        if (changes.getNotes() != null && changes.getNotes().length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }
        return encounterRepository.findById(id).map(existing -> {
            if (changes.getVersion() != null && !changes.getVersion().equals(existing.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(Encounter.class, id);
            }
            if (changes.getEncounterType() != null) existing.setEncounterType(changes.getEncounterType());
            if (changes.getEncounterDateTime() != null) existing.setEncounterDateTime(changes.getEncounterDateTime());
            if (changes.getAdmitDateTime() != null) existing.setAdmitDateTime(changes.getAdmitDateTime());
            if (changes.getDischargeDateTime() != null) existing.setDischargeDateTime(changes.getDischargeDateTime());
            if (changes.getDepartment() != null) existing.setDepartment(changes.getDepartment());
            if (changes.getRoom() != null) existing.setRoom(changes.getRoom());
            if (changes.getBed() != null) existing.setBed(changes.getBed());
            if (changes.getChiefComplaint() != null) existing.setChiefComplaint(changes.getChiefComplaint());
            if (changes.getPriority() != null) existing.setPriority(changes.getPriority());
            if (changes.getVisitType() != null) existing.setVisitType(changes.getVisitType());
            if (changes.getNotes() != null) existing.setNotes(changes.getNotes());
            log.info("Updating encounter {}", existing.getEncounterNumber());
            return EncounterResponseDTO.from(encounterRepository.saveAndFlush(existing));
        });
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Check in encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> checkIn(Long encounterId) {
        return changeStatus(encounterId, EncounterStatus.CHECKED_IN, null, "Patient checked in for encounter {}");
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Start encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> startEncounter(Long encounterId) {
        return changeStatus(encounterId, EncounterStatus.IN_PROGRESS, null, "Encounter {} started");
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Complete encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> completeEncounter(Long encounterId, String notes) {
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("notes must not exceed " + MAX_NOTES_LENGTH + " characters");
        }
        return changeStatus(encounterId, EncounterStatus.COMPLETED, notes, "Encounter {} completed");
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Cancel encounter",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> cancelEncounter(Long encounterId) {
        return changeStatus(encounterId, EncounterStatus.CANCELLED, null, "Encounter {} cancelled");
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter", description = "Mark encounter no-show",
            patientIdFromArgs = false)
    public Optional<EncounterResponseDTO> markNoShow(Long encounterId) {
        return changeStatus(encounterId, EncounterStatus.NO_SHOW, null, "Encounter {} marked as no-show");
    }

    private Optional<EncounterResponseDTO> changeStatus(Long encounterId, EncounterStatus status, String notes,
                                                        String logMessage) {
        return encounterRepository.findById(encounterId).map(enc -> {
            enc.setStatus(status);
            if (notes != null) {
                enc.setNotes(notes);
            }
            Encounter saved = encounterRepository.save(enc);
            log.info(logMessage, saved.getEncounterNumber());
            return EncounterResponseDTO.from(saved);
        });
    }

    private static List<EncounterResponseDTO> toResponses(List<Encounter> encounters) {
        return encounters.stream().map(EncounterResponseDTO::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getPatientEncounterCount(Long patientId) {
        return encounterRepository.countByPatientId(patientId);
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
