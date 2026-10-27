package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final int MAX_NOTES_LENGTH = 1000;

    private final EncounterRepository encounterRepository;
    private final PatientRepository patientRepository;
    private final ProviderRepository providerRepository;
    private final EncounterMapper encounterMapper;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository,
                            PatientRepository patientRepository,
                            ProviderRepository providerRepository,
                            EncounterMapper encounterMapper) {
        this.encounterRepository = encounterRepository;
        this.patientRepository = patientRepository;
        this.providerRepository = providerRepository;
        this.encounterMapper = encounterMapper;
    }

    @Transactional(readOnly = true)
    public Optional<EncounterDTO> findById(Long id) {
        return encounterRepository.findById(id).map(encounterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<EncounterDTO> findByIdWithDetails(Long id) {
        return encounterRepository.findByIdWithDetails(id).map(encounterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<EncounterDTO> findByEncounterNumber(String encounterNumber) {
        return encounterRepository.findByEncounterNumber(encounterNumber).map(encounterMapper::toDto);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters")
    public List<EncounterDTO> findByPatientId(Long patientId) {
        return toDtos(encounterRepository.findByPatientId(patientId));
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "List patient encounters (paged)")
    public Page<EncounterDTO> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable).map(encounterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<EncounterDTO> findByProviderId(Long providerId) {
        return toDtos(encounterRepository.findByAttendingProviderId(providerId));
    }

    @Transactional(readOnly = true)
    public List<EncounterDTO> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return toDtos(encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay));
    }

    @Transactional(readOnly = true)
    public List<EncounterDTO> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return toDtos(encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay()));
    }

    @Transactional(readOnly = true)
    public List<EncounterDTO> findByStatus(EncounterStatus status) {
        return toDtos(encounterRepository.findByStatus(status));
    }

    public EncounterDTO create(EncounterDTO request) {
        if (request.getPatient() == null || request.getPatient().getId() == null) {
            throw new IllegalArgumentException("patient.id is required");
        }
        if (request.getEncounterType() == null) {
            throw new IllegalArgumentException("encounterType is required");
        }
        if (request.getEncounterDateTime() == null) {
            throw new IllegalArgumentException("encounterDateTime is required");
        }

        Patient patient = patientRepository.findById(request.getPatient().getId())
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + request.getPatient().getId()));

        Encounter encounter = new Encounter();
        encounter.setPatient(patient);
        encounter.setAttendingProvider(resolveProvider(request));
        encounterMapper.applyEditableFields(request, encounter);
        encounter.setEncounterNumber(generateEncounterNumber());
        encounter.setStatus(EncounterStatus.SCHEDULED);

        Encounter saved = encounterRepository.saveAndFlush(encounter);
        log.info("Created encounter {} for patient id {}", saved.getEncounterNumber(), patient.getId());
        return encounterMapper.toDto(saved);
    }

    public EncounterDTO update(Long id, EncounterDTO request) {
        Encounter existing = loadEncounter(id);

        if (existing.getStatus() != null && existing.getStatus().isTerminal()) {
            throw new InvalidEncounterStateException(
                    "Encounter " + existing.getEncounterNumber() + " is " + existing.getStatus() + " and can no longer be modified");
        }
        if (request.getVersion() != null && !request.getVersion().equals(existing.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Encounter.class, id);
        }
        if (request.getPatient() != null && request.getPatient().getId() != null
                && !request.getPatient().getId().equals(existing.getPatient().getId())) {
            throw new IllegalArgumentException("An encounter cannot be moved to a different patient");
        }

        if (request.getAttendingProvider() != null || request.isExplicitlySet("attendingProvider")) {
            existing.setAttendingProvider(resolveProvider(request));
        }
        encounterMapper.applyEditableFields(request, existing);

        Encounter saved = encounterRepository.saveAndFlush(existing);
        log.info("Updated encounter {}", saved.getEncounterNumber());
        return encounterMapper.toDto(saved);
    }

    public EncounterDTO checkIn(Long encounterId) {
        Encounter enc = transition(encounterId, EncounterStatus.CHECKED_IN);
        log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        return encounterMapper.toDto(enc);
    }

    public EncounterDTO startEncounter(Long encounterId) {
        Encounter enc = transition(encounterId, EncounterStatus.IN_PROGRESS);
        log.info("Encounter {} started", enc.getEncounterNumber());
        return encounterMapper.toDto(enc);
    }

    public EncounterDTO completeEncounter(Long encounterId, String notes) {
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("notes must be at most " + MAX_NOTES_LENGTH + " characters");
        }
        Encounter enc = loadEncounter(encounterId);
        assertTransition(enc, EncounterStatus.COMPLETED);
        enc.setStatus(EncounterStatus.COMPLETED);
        if (notes != null) {
            enc.setNotes(notes);
        }
        Encounter saved = encounterRepository.saveAndFlush(enc);
        log.info("Encounter {} completed", saved.getEncounterNumber());
        return encounterMapper.toDto(saved);
    }

    public EncounterDTO cancelEncounter(Long encounterId) {
        Encounter enc = transition(encounterId, EncounterStatus.CANCELLED);
        log.info("Encounter {} cancelled", enc.getEncounterNumber());
        return encounterMapper.toDto(enc);
    }

    public EncounterDTO markNoShow(Long encounterId) {
        Encounter enc = transition(encounterId, EncounterStatus.NO_SHOW);
        log.info("Encounter {} marked as no-show", enc.getEncounterNumber());
        return encounterMapper.toDto(enc);
    }

    @Transactional(readOnly = true)
    public long getPatientEncounterCount(Long patientId) {
        return encounterRepository.countByPatientId(patientId);
    }

    private Encounter transition(Long encounterId, EncounterStatus target) {
        Encounter enc = loadEncounter(encounterId);
        assertTransition(enc, target);
        enc.setStatus(target);
        return encounterRepository.saveAndFlush(enc);
    }

    private void assertTransition(Encounter enc, EncounterStatus target) {
        EncounterStatus current = enc.getStatus();
        if (current == null || !current.canTransitionTo(target)) {
            throw new InvalidEncounterStateException(
                    "Encounter " + enc.getEncounterNumber() + " cannot move from " + current + " to " + target);
        }
    }

    private Encounter loadEncounter(Long encounterId) {
        return encounterRepository.findById(encounterId)
                .orElseThrow(() -> new EntityNotFoundException("Encounter not found with id: " + encounterId));
    }

    private Provider resolveProvider(EncounterDTO request) {
        if (request.getAttendingProvider() == null || request.getAttendingProvider().getId() == null) {
            return null;
        }
        Long providerId = request.getAttendingProvider().getId();
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found with id: " + providerId));
    }

    private List<EncounterDTO> toDtos(List<Encounter> encounters) {
        return encounters.stream().map(encounterMapper::toDto).collect(Collectors.toList());
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
