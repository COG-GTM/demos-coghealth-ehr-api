package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderRepository providerRepository;

    public ProviderService(ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Provider", description = "List all providers")
    public List<Provider> findAll() {
        return providerRepository.findAll();
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Provider", description = "List active providers")
    public List<Provider> findActive() {
        return providerRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Provider", description = "Find provider by ID")
    public Optional<Provider> findById(Long id) {
        return providerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.READ, resourceType = "Provider", description = "Find provider by NPI")
    public Optional<Provider> findByNpi(String npi) {
        return providerRepository.findByNpi(npi);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Provider", description = "Find providers by department")
    public List<Provider> findByDepartment(String department) {
        return providerRepository.findByDepartment(department);
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Provider", description = "Find providers by specialty")
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

    @AuditAccess(action = AuditAction.CREATE, resourceType = "Provider", description = "Save provider")
    public Provider save(Provider provider) {
        log.info("Saving provider: {} {}", provider.getFirstName(), provider.getLastName());
        return providerRepository.save(provider);
    }

    @AuditAccess(action = AuditAction.UPDATE, resourceType = "Provider", description = "Deactivate provider")
    public void deactivate(Long id) {
        providerRepository.findById(id).ifPresent(provider -> {
            provider.setActive(false);
            providerRepository.save(provider);
            log.info("Deactivated provider: {}", provider.getNpi());
        });
    }

    @Transactional(readOnly = true)
    @AuditAccess(action = AuditAction.SEARCH, resourceType = "Provider", description = "Search providers by last name")
    public List<Provider> search(String lastName) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName);
    }
}
