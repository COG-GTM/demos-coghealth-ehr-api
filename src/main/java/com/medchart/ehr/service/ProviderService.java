package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.ProviderAuditLogger;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProviderService {

    private final ProviderRepository providerRepository;
    private final ProviderAuditLogger providerAuditLogger;

    public ProviderService(ProviderRepository providerRepository, ProviderAuditLogger providerAuditLogger) {
        this.providerRepository = providerRepository;
        this.providerAuditLogger = providerAuditLogger;
    }

    @Transactional(readOnly = true)
    public List<Provider> findAll() {
        return providerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Provider> findActive() {
        return providerRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public Optional<Provider> findById(Long id) {
        return providerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Provider> findByNpi(String npi) {
        return providerRepository.findByNpi(npi);
    }

    @Transactional(readOnly = true)
    public List<Provider> findByDepartment(String department) {
        return providerRepository.findByDepartment(department);
    }

    @Transactional(readOnly = true)
    public List<Provider> findBySpecialty(String specialty) {
        return providerRepository.findBySpecialty(specialty);
    }

    @Transactional(readOnly = true)
    public List<String> getAllDepartments() {
        return providerRepository.findAllDepartments();
    }

    @Transactional(readOnly = true)
    public List<String> getAllSpecialties() {
        return providerRepository.findAllSpecialties();
    }

    public Provider save(Provider provider) {
        boolean isCreate = provider.getId() == null;
        AuditAction action = isCreate ? AuditAction.CREATE : AuditAction.UPDATE;
        try {
            Provider saved = providerRepository.save(provider);
            providerAuditLogger.logChange(action, saved.getId(), saved.getNpi(),
                    (isCreate ? "Created" : "Updated") + " provider directory record");
            return saved;
        } catch (RuntimeException e) {
            providerAuditLogger.logFailedChange(action, provider.getId(), provider.getNpi(),
                    (isCreate ? "Create" : "Update") + " of provider directory record failed", e.getMessage());
            throw e;
        }
    }

    public void deactivate(Long id) {
        Optional<Provider> existing = providerRepository.findById(id);
        if (!existing.isPresent()) {
            providerAuditLogger.logFailedChange(AuditAction.DELETE, id, null,
                    "Deactivation requested for unknown provider", "Provider not found");
            return;
        }

        Provider provider = existing.get();
        boolean wasActive = Boolean.TRUE.equals(provider.getActive());
        try {
            provider.setActive(false);
            providerRepository.save(provider);
        } catch (RuntimeException e) {
            providerAuditLogger.logFailedChange(AuditAction.DELETE, id, provider.getNpi(),
                    "Deactivation of provider failed", e.getMessage());
            throw e;
        }
        providerAuditLogger.logChange(AuditAction.DELETE, id, provider.getNpi(),
                "Deactivated provider (active " + wasActive + " -> false)");
    }

    @Transactional(readOnly = true)
    public List<Provider> search(String lastName) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName);
    }
}
