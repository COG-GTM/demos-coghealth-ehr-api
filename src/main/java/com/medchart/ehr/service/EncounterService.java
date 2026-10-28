package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.repository.EncounterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    
    private final EncounterRepository encounterRepository;
    private final PatientAccessLogger patientAccessLogger;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository, PatientAccessLogger patientAccessLogger) {
        this.encounterRepository = encounterRepository;
        this.patientAccessLogger = patientAccessLogger;
    }

    public Optional<Encounter> findByIdForRequester(Long id, User requester) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(id);
        encounter.ifPresent(enc -> {
            assertCanAccess(enc, requester);
            log.info("User {} accessed encounter id {}", requester.getId(), enc.getId());
            logPatientAccess(requester, enc.getPatient().getId(), AuditAction.READ, "View encounter");
        });
        return encounter;
    }

    public Optional<Encounter> findByEncounterNumberForRequester(String encounterNumber, User requester) {
        Optional<Encounter> encounter = encounterRepository.findByEncounterNumber(encounterNumber);
        encounter.ifPresent(enc -> {
            assertCanAccess(enc, requester);
            log.info("User {} accessed encounter id {}", requester.getId(), enc.getId());
            logPatientAccess(requester, enc.getPatient().getId(), AuditAction.READ, "View encounter");
        });
        return encounter;
    }

    public List<Encounter> findByPatientId(Long patientId, User requester) {
        List<Encounter> encounters;
        if (isAdmin(requester)) {
            encounters = encounterRepository.findByPatientId(patientId);
        } else {
            if (requester.getProviderId() == null) {
                return Collections.emptyList();
            }
            encounters = encounterRepository.findByPatientIdAndAttendingProviderId(patientId, requester.getProviderId());
        }
        logPatientAccess(requester, patientId, AuditAction.READ, "List patient encounters");
        return encounters;
    }

    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable, User requester) {
        Page<Encounter> encounters;
        if (isAdmin(requester)) {
            encounters = encounterRepository.findByPatientId(patientId, pageable);
        } else {
            if (requester.getProviderId() == null) {
                return new PageImpl<>(Collections.emptyList(), pageable, 0);
            }
            encounters = encounterRepository.findByPatientIdAndAttendingProviderId(patientId, requester.getProviderId(), pageable);
        }
        logPatientAccess(requester, patientId, AuditAction.READ, "List patient encounters");
        return encounters;
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByProviderId(Long providerId, User requester) {
        assertCanAccessProvider(providerId, requester);
        return encounterRepository.findByAttendingProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date, User requester) {
        assertCanAccessProvider(providerId, requester);
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        return encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate, User requester) {
        if (!isAdmin(requester)) {
            if (requester.getProviderId() == null) {
                return Collections.emptyList();
            }
            return encounterRepository.findByDateRangeAndProvider(
                    startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay(), requester.getProviderId());
        }
        return encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByStatus(EncounterStatus status, User requester) {
        if (!isAdmin(requester)) {
            if (requester.getProviderId() == null) {
                return Collections.emptyList();
            }
            return encounterRepository.findByStatusAndAttendingProviderId(status, requester.getProviderId());
        }
        return encounterRepository.findByStatus(status);
    }

    public Encounter create(Encounter encounter, User requester) {
        assertOwnProvider(encounter, requester);
        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return saved;
    }

    public Optional<Encounter> update(Long id, Encounter encounter, User requester) {
        Optional<Encounter> existing = encounterRepository.findByIdWithDetails(id);
        if (!existing.isPresent()) {
            return Optional.empty();
        }
        Encounter existingEncounter = existing.get();
        assertCanAccess(existingEncounter, requester);
        assertOwnProvider(encounter, requester);
        if (!isAdmin(requester)) {
            if (encounter.getPatient() == null) {
                encounter.setPatient(existingEncounter.getPatient());
            } else if (!Objects.equals(encounter.getPatient().getId(), existingEncounter.getPatient().getId())) {
                throw new AccessDeniedException("Not authorized to reassign encounter to another patient");
            }
        }
        encounter.setId(id);
        log.info("Updating encounter {}", encounter.getEncounterNumber());
        Encounter updated = encounterRepository.save(encounter);
        logPatientAccess(requester, updated.getPatient().getId(), AuditAction.UPDATE, "Update encounter");
        return Optional.of(updated);
    }

    public boolean checkIn(Long encounterId, User requester) {
        return updateStatus(encounterId, requester, enc -> {
            enc.setStatus(EncounterStatus.CHECKED_IN);
            log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        });
    }

    public boolean startEncounter(Long encounterId, User requester) {
        return updateStatus(encounterId, requester, enc -> {
            enc.setStatus(EncounterStatus.IN_PROGRESS);
            log.info("Encounter {} started", enc.getEncounterNumber());
        });
    }

    public boolean completeEncounter(Long encounterId, String notes, User requester) {
        return updateStatus(encounterId, requester, enc -> {
            enc.setStatus(EncounterStatus.COMPLETED);
            if (notes != null) {
                enc.setNotes(notes);
            }
            log.info("Encounter {} completed", enc.getEncounterNumber());
        });
    }

    public boolean cancelEncounter(Long encounterId, User requester) {
        return updateStatus(encounterId, requester, enc -> {
            enc.setStatus(EncounterStatus.CANCELLED);
            log.info("Encounter {} cancelled", enc.getEncounterNumber());
        });
    }

    public boolean markNoShow(Long encounterId, User requester) {
        return updateStatus(encounterId, requester, enc -> {
            enc.setStatus(EncounterStatus.NO_SHOW);
            log.info("Encounter {} marked as no-show", enc.getEncounterNumber());
        });
    }

    @Transactional(readOnly = true)
    public long getPatientEncounterCount(Long patientId) {
        return encounterRepository.countByPatientId(patientId);
    }

    private boolean updateStatus(Long encounterId, User requester, Consumer<Encounter> update) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(encounterId);
        if (!encounter.isPresent()) {
            return false;
        }
        Encounter existing = encounter.get();
        assertCanAccess(existing, requester);
        update.accept(existing);
        encounterRepository.save(existing);
        logPatientAccess(requester, existing.getPatient().getId(), AuditAction.UPDATE, "Update encounter status");
        return true;
    }

    private void logPatientAccess(User requester, Long patientId, AuditAction action, String reason) {
        String roles = requester.getRoles() == null
                ? ""
                : requester.getRoles().stream().map(User.Role::name).sorted().collect(Collectors.joining(","));
        ServletRequestAttributes attributes = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes
                ? (ServletRequestAttributes) RequestContextHolder.getRequestAttributes()
                : null;
        String clientIp = attributes == null ? null : attributes.getRequest().getRemoteAddr();
        patientAccessLogger.logAccess(
                requester.getId(), roles, patientId, null, action, "Encounter", reason, clientIp, null);
    }

    private void assertCanAccessProvider(Long providerId, User requester) {
        if (!isAdmin(requester) && !providerId.equals(requester.getProviderId())) {
            throw new AccessDeniedException("Not authorized to access provider encounters");
        }
    }

    private void assertOwnProvider(Encounter encounter, User requester) {
        if (!isAdmin(requester)
                && (requester.getProviderId() == null
                || encounter.getAttendingProvider() == null
                || !requester.getProviderId().equals(encounter.getAttendingProvider().getId()))) {
            throw new AccessDeniedException("Not authorized to assign encounter to this provider");
        }
    }

    private void assertCanAccess(Encounter encounter, User requester) {
        if (!canAccess(encounter, requester)) {
            throw new AccessDeniedException("Not authorized to access encounter");
        }
    }

    private boolean canAccess(Encounter encounter, User requester) {
        return isAdmin(requester)
                || (requester.getProviderId() != null
                && encounter.getAttendingProvider() != null
                && requester.getProviderId().equals(encounter.getAttendingProvider().getId()));
    }

    private boolean isAdmin(User requester) {
        return requester.getRoles() != null && requester.getRoles().contains(User.Role.ADMIN);
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
