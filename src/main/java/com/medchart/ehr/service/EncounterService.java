package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterCreateRequest;
import com.medchart.ehr.dto.EncounterUpdateRequest;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Transactional
public class EncounterService {

    private static final Logger log = LoggerFactory.getLogger(EncounterService.class);
    private static final DateTimeFormatter ENC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    
    private final EncounterRepository encounterRepository;
    private final PatientRepository patientRepository;
    private final ProviderRepository providerRepository;
    private final AuditService auditService;
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository,
                            PatientRepository patientRepository,
                            ProviderRepository providerRepository,
                            AuditService auditService) {
        this.encounterRepository = encounterRepository;
        this.patientRepository = patientRepository;
        this.providerRepository = providerRepository;
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

    public Encounter create(EncounterCreateRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new InvalidEncounterRequestException("Unknown patientId"));
        String encNumber = generateEncounterNumber();

        Encounter encounter = Encounter.builder()
                .encounterNumber(encNumber)
                .status(EncounterStatus.SCHEDULED)
                .patient(patient)
                .attendingProvider(resolveProvider(request.getAttendingProviderId()))
                .encounterType(request.getEncounterType())
                .encounterDateTime(request.getEncounterDateTime())
                .admitDateTime(request.getAdmitDateTime())
                .dischargeDateTime(request.getDischargeDateTime())
                .department(request.getDepartment())
                .room(request.getRoom())
                .bed(request.getBed())
                .chiefComplaint(request.getChiefComplaint())
                .priority(request.getPriority())
                .visitType(request.getVisitType())
                .notes(request.getNotes())
                .createdBy(currentUsername())
                .build();

        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient id {}", encNumber, patient.getId());
        recordAudit(AuditAction.CREATE, saved, "Create encounter");
        return saved;
    }

    public Optional<Encounter> update(Long id, EncounterUpdateRequest request) {
        return encounterRepository.findById(id).map(existing -> {
            applyUpdate(existing, request);
            log.info("Updating encounter {}", existing.getEncounterNumber());
            Encounter saved = encounterRepository.save(existing);
            recordAudit(AuditAction.UPDATE, saved, "Update encounter");
            return unproxyAssociations(saved);
        });
    }

    private Encounter unproxyAssociations(Encounter encounter) {
        encounter.setPatient((Patient) Hibernate.unproxy(encounter.getPatient()));
        encounter.setAttendingProvider((Provider) Hibernate.unproxy(encounter.getAttendingProvider()));
        return encounter;
    }

    private void applyUpdate(Encounter encounter, EncounterUpdateRequest request) {
        if (request.isProvided(EncounterUpdateRequest.ATTENDING_PROVIDER_ID)) {
            encounter.setAttendingProvider(resolveProvider(request.getAttendingProviderId()));
        }
        if (request.isProvided(EncounterUpdateRequest.ENCOUNTER_TYPE)) {
            encounter.setEncounterType(request.getEncounterType());
        }
        if (request.isProvided(EncounterUpdateRequest.ENCOUNTER_DATE_TIME)) {
            encounter.setEncounterDateTime(request.getEncounterDateTime());
        }
        if (request.isProvided(EncounterUpdateRequest.ADMIT_DATE_TIME)) {
            encounter.setAdmitDateTime(request.getAdmitDateTime());
        }
        if (request.isProvided(EncounterUpdateRequest.DISCHARGE_DATE_TIME)) {
            encounter.setDischargeDateTime(request.getDischargeDateTime());
        }
        if (request.isProvided(EncounterUpdateRequest.DEPARTMENT)) {
            encounter.setDepartment(request.getDepartment());
        }
        if (request.isProvided(EncounterUpdateRequest.ROOM)) {
            encounter.setRoom(request.getRoom());
        }
        if (request.isProvided(EncounterUpdateRequest.BED)) {
            encounter.setBed(request.getBed());
        }
        if (request.isProvided(EncounterUpdateRequest.CHIEF_COMPLAINT)) {
            encounter.setChiefComplaint(request.getChiefComplaint());
        }
        if (request.isProvided(EncounterUpdateRequest.PRIORITY)) {
            encounter.setPriority(request.getPriority());
        }
        if (request.isProvided(EncounterUpdateRequest.VISIT_TYPE)) {
            encounter.setVisitType(request.getVisitType());
        }
        if (request.isProvided(EncounterUpdateRequest.NOTES)) {
            encounter.setNotes(request.getNotes());
        }
    }

    private Provider resolveProvider(Long providerId) {
        if (providerId == null) {
            return null;
        }
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new InvalidEncounterRequestException("Unknown attendingProviderId"));
    }

    private void recordAudit(AuditAction action, Encounter encounter, String description) {
        String username = currentUsername();
        HttpServletRequest request = currentRequest();
        HttpSession session = request != null ? request.getSession(false) : null;
        AuditEvent event = AuditEvent.builder()
                .userId(username != null ? username : "anonymous")
                .userName(username)
                .patientId(encounter.getPatient() != null ? encounter.getPatient().getId() : null)
                .action(action)
                .resourceType("Encounter")
                .resourceId(encounter.getId())
                .description(description)
                .ipAddress(request != null ? clientIp(request) : null)
                .userAgent(request != null ? truncate(request.getHeader("User-Agent"), 200) : null)
                .sessionId(session != null ? session.getId() : null)
                .build();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
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

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        return truncate(request.getRemoteAddr(), 50);
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    public void checkIn(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CHECKED_IN);
            encounterRepository.save(enc);
            log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        });
    }

    public void startEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.IN_PROGRESS);
            encounterRepository.save(enc);
            log.info("Encounter {} started", enc.getEncounterNumber());
        });
    }

    public void completeEncounter(Long encounterId, String notes) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.COMPLETED);
            if (notes != null) {
                enc.setNotes(notes);
            }
            encounterRepository.save(enc);
            log.info("Encounter {} completed", enc.getEncounterNumber());
        });
    }

    public void cancelEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CANCELLED);
            encounterRepository.save(enc);
            log.info("Encounter {} cancelled", enc.getEncounterNumber());
        });
    }

    public void markNoShow(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.NO_SHOW);
            encounterRepository.save(enc);
            log.info("Encounter {} marked as no-show", enc.getEncounterNumber());
        });
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
