package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.audit.CurrentActor;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    
    private static final int MAX_NOTES_LENGTH = 1000;

    private static final Set<EncounterStatus> TERMINAL_STATUSES =
            EnumSet.of(EncounterStatus.COMPLETED, EncounterStatus.CANCELLED, EncounterStatus.NO_SHOW);

    private static final Map<EncounterStatus, Set<EncounterStatus>> ALLOWED_TRANSITIONS = Map.of(
            EncounterStatus.SCHEDULED,
            EnumSet.of(EncounterStatus.CHECKED_IN, EncounterStatus.CANCELLED, EncounterStatus.NO_SHOW),
            EncounterStatus.CHECKED_IN,
            EnumSet.of(EncounterStatus.IN_PROGRESS, EncounterStatus.CANCELLED, EncounterStatus.NO_SHOW),
            EncounterStatus.IN_PROGRESS,
            EnumSet.of(EncounterStatus.COMPLETED));

    private final EncounterRepository encounterRepository;
    private final AuditService auditService;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository, AuditService auditService) {
        this.encounterRepository = encounterRepository;
        this.auditService = auditService;
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
    public Optional<Encounter> findByEncounterNumber(String encounterNumber) {
        return encounterRepository.findByEncounterNumber(encounterNumber);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByPatientId(Long patientId) {
        return encounterRepository.findByPatientId(patientId);
    }

    @Transactional(readOnly = true)
    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByProviderId(Long providerId) {
        return encounterRepository.findByAttendingProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByStatus(EncounterStatus status) {
        return encounterRepository.findByStatus(status);
    }

    public Encounter create(Encounter encounter) {
        String encNumber = generateEncounterNumber();
        encounter.setId(null);
        encounter.setVersion(null);
        encounter.setNotes(null);
        encounter.setCreatedBy(CurrentActor.username());
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return saved;
    }

    public Encounter update(Long encounterId, Encounter changes) {
        Encounter enc = load(encounterId);
        if (TERMINAL_STATUSES.contains(enc.getStatus())) {
            auditUpdate(enc, "Update rejected", false, "Encounter is " + enc.getStatus());
            throw new InvalidEncounterTransitionException(
                    "Encounter " + enc.getEncounterNumber() + " is " + enc.getStatus() + " and can no longer be edited");
        }
        if (changes.getVersion() != null && !changes.getVersion().equals(enc.getVersion())) {
            auditUpdate(enc, "Update rejected", false, "Stale version");
            throw new InvalidEncounterTransitionException(
                    "Encounter " + enc.getEncounterNumber() + " was modified by another user; reload and retry");
        }

        if (changes.getEncounterType() != null) enc.setEncounterType(changes.getEncounterType());
        if (changes.getDepartment() != null) enc.setDepartment(changes.getDepartment());
        if (changes.getRoom() != null) enc.setRoom(changes.getRoom());
        if (changes.getBed() != null) enc.setBed(changes.getBed());
        if (changes.getChiefComplaint() != null) enc.setChiefComplaint(changes.getChiefComplaint());
        if (changes.getPriority() != null) enc.setPriority(changes.getPriority());
        if (changes.getVisitType() != null) enc.setVisitType(changes.getVisitType());

        Encounter saved = encounterRepository.save(enc);
        auditUpdate(saved, "Updated encounter details", true, null);
        log.info("Encounter {} updated by {}", saved.getEncounterNumber(), CurrentActor.username());
        saved.setPatient((Patient) Hibernate.unproxy(saved.getPatient()));
        saved.setAttendingProvider((Provider) Hibernate.unproxy(saved.getAttendingProvider()));
        return saved;
    }

    public void checkIn(Long encounterId) {
        transition(encounterId, EncounterStatus.CHECKED_IN);
    }

    public void startEncounter(Long encounterId) {
        transition(encounterId, EncounterStatus.IN_PROGRESS);
    }

    public void completeEncounter(Long encounterId, String notes) {
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notes must be at most " + MAX_NOTES_LENGTH + " characters");
        }
        Encounter enc = transition(encounterId, EncounterStatus.COMPLETED);
        if (notes != null && !notes.isBlank()) {
            enc.setNotes(notes);
            encounterRepository.save(enc);
        }
    }

    public void cancelEncounter(Long encounterId) {
        transition(encounterId, EncounterStatus.CANCELLED);
    }

    public void markNoShow(Long encounterId) {
        transition(encounterId, EncounterStatus.NO_SHOW);
    }

    public static boolean canTransition(EncounterStatus from, EncounterStatus to) {
        return from != null && ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    private Encounter transition(Long encounterId, EncounterStatus target) {
        Encounter enc = load(encounterId);
        EncounterStatus current = enc.getStatus();
        String description = "Status " + current + " -> " + target;
        if (!canTransition(current, target)) {
            auditUpdate(enc, description, false, "Transition not allowed");
            log.warn("Rejected encounter {} transition {} by {}", enc.getEncounterNumber(), description, CurrentActor.username());
            throw new InvalidEncounterTransitionException(current, target);
        }
        enc.setStatus(target);
        Encounter saved = encounterRepository.save(enc);
        auditUpdate(saved, description, true, null);
        log.info("Encounter {} {} by {}", saved.getEncounterNumber(), description, CurrentActor.username());
        return saved;
    }

    private Encounter load(Long encounterId) {
        return encounterRepository.findById(encounterId)
                .orElseThrow(() -> new EncounterNotFoundException(encounterId));
    }

    private void auditUpdate(Encounter enc, String description, boolean success, String error) {
        String actor = CurrentActor.username();
        AuditEvent event = AuditEvent.builder()
                .userId(actor)
                .userName(actor)
                .patientId(enc.getPatient() != null ? enc.getPatient().getId() : null)
                .action(AuditAction.UPDATE)
                .resourceType("Encounter")
                .resourceId(enc.getId())
                .description(description)
                .success(success)
                .errorMessage(error)
                .build();
        if (success && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    auditService.saveAuditEventAsync(event);
                }
            });
        } else {
            auditService.saveAuditEventAsync(event);
        }
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
