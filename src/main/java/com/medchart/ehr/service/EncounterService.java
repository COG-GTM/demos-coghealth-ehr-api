package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.repository.EncounterRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AtomicLong encounterSequence = new AtomicLong(100);

    public EncounterService(EncounterRepository encounterRepository) {
        this.encounterRepository = encounterRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "encounters", key = "#id", unless = "!#result.isPresent()")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by ID")
    public Optional<Encounter> findById(Long id) {
        Optional<Encounter> encounter = encounterRepository.findById(id);
        encounter.ifPresent(this::initializeLazyCollections);
        return encounter;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "encounters", key = "'details-' + #id", unless = "!#result.isPresent()")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter with details")
    public Optional<Encounter> findByIdWithDetails(Long id) {
        Optional<Encounter> encounter = encounterRepository.findByIdWithDetails(id);
        encounter.ifPresent(this::initializeLazyCollections);
        return encounter;
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter by number")
    public Optional<Encounter> findByEncounterNumber(String encounterNumber) {
        Optional<Encounter> encounter = encounterRepository.findByEncounterNumber(encounterNumber);
        encounter.ifPresent(this::initializeLazyCollections);
        return encounter;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "encountersByPatient", key = "#patientId")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounters by patient")
    public List<Encounter> findByPatientId(Long patientId) {
        List<Encounter> encounters = encounterRepository.findByPatientId(patientId);
        encounters.forEach(this::initializeLazyCollections);
        return encounters;
    }

    @Transactional(readOnly = true)
    public Page<Encounter> findByPatientId(Long patientId, Pageable pageable) {
        return encounterRepository.findByPatientId(patientId, pageable);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "encountersByProvider", key = "#providerId")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounters by provider")
    public List<Encounter> findByProviderId(Long providerId) {
        List<Encounter> encounters = encounterRepository.findByAttendingProviderId(providerId);
        encounters.forEach(this::initializeLazyCollections);
        return encounters;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "providerSchedule", key = "#providerId + '-' + #date.toString()")
    @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View provider schedule")
    public List<Encounter> getProviderSchedule(Long providerId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();
        List<Encounter> encounters = encounterRepository.findTodaysSchedule(providerId, startOfDay, endOfDay);
        encounters.forEach(this::initializeLazyCollections);
        return encounters;
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return encounterRepository.findByDateRange(startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public List<Encounter> findByStatus(EncounterStatus status) {
        return encounterRepository.findByStatus(status);
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
    public Encounter create(Encounter encounter) {
        String encNumber = generateEncounterNumber();
        encounter.setEncounterNumber(encNumber);
        encounter.setStatus(EncounterStatus.SCHEDULED);
        
        Encounter saved = encounterRepository.save(encounter);
        log.info("Created encounter {} for patient {}", encNumber, encounter.getPatient().getMrn());
        return saved;
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
    public Encounter update(Encounter encounter) {
        log.info("Updating encounter {}", encounter.getEncounterNumber());
        return encounterRepository.save(encounter);
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
    public void checkIn(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CHECKED_IN);
            encounterRepository.save(enc);
            log.info("Patient checked in for encounter {}", enc.getEncounterNumber());
        });
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
    public void startEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.IN_PROGRESS);
            encounterRepository.save(enc);
            log.info("Encounter {} started", enc.getEncounterNumber());
        });
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
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

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
    public void cancelEncounter(Long encounterId) {
        encounterRepository.findById(encounterId).ifPresent(enc -> {
            enc.setStatus(EncounterStatus.CANCELLED);
            encounterRepository.save(enc);
            log.info("Encounter {} cancelled", enc.getEncounterNumber());
        });
    }

    @Caching(evict = {
            @CacheEvict(value = "encounters", allEntries = true),
            @CacheEvict(value = "encountersByPatient", allEntries = true),
            @CacheEvict(value = "encountersByProvider", allEntries = true),
            @CacheEvict(value = "providerSchedule", allEntries = true)
    })
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

    private void initializeLazyCollections(Encounter encounter) {
        Hibernate.initialize(encounter.getDiagnoses());
        encounter.getDiagnoses().forEach(ed -> {
            if (ed.getDiagnosis() != null) {
                Hibernate.initialize(ed.getDiagnosis());
                Hibernate.initialize(ed.getDiagnosis().getDiagnosedBy());
            }
        });
        if (encounter.getPatient() != null) {
            Hibernate.initialize(encounter.getPatient().getIdentifiers());
            Hibernate.initialize(encounter.getPatient().getEmergencyContacts());
        }
        if (encounter.getAttendingProvider() != null) {
            Hibernate.initialize(encounter.getAttendingProvider().getLicenses());
        }
    }

    private String generateEncounterNumber() {
        String year = LocalDate.now().format(ENC_DATE_FORMAT);
        long seq = encounterSequence.incrementAndGet();
        return String.format("ENC-%s-%06d", year, seq);
    }
}
