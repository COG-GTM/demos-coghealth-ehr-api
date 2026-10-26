package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
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

    public Provider create(ProviderDTO request) {
        assertNpiAvailable(request.getNpi(), null);
        Provider provider = new Provider();
        applyWritableFields(request, provider);
        Provider saved = providerRepository.save(provider);
        log.info("Created provider id={}", saved.getId());
        return saved;
    }

    public Optional<Provider> update(Long id, ProviderDTO request) {
        return providerRepository.findById(id).map(existing -> {
            assertNpiAvailable(request.getNpi(), id);
            applyWritableFields(request, existing);
            Provider saved = providerRepository.save(existing);
            log.info("Updated provider id={}", id);
            return saved;
        });
    }

    public boolean deactivate(Long id) {
        return providerRepository.findById(id).map(provider -> {
            provider.setActive(false);
            providerRepository.save(provider);
            log.info("Deactivated provider id={}", id);
            return true;
        }).orElse(false);
    }

    private void assertNpiAvailable(String npi, Long currentId) {
        providerRepository.findByNpi(npi)
                .filter(other -> !other.getId().equals(currentId))
                .ifPresent(other -> {
                    throw new DuplicateNpiException();
                });
    }

    private static void applyWritableFields(ProviderDTO source, Provider target) {
        target.setNpi(source.getNpi());
        target.setFirstName(source.getFirstName());
        target.setMiddleName(source.getMiddleName());
        target.setLastName(source.getLastName());
        target.setSuffix(source.getSuffix());
        target.setCredentials(source.getCredentials());
        target.setProviderType(source.getProviderType());
        target.setSpecialty(source.getSpecialty());
        target.setSubspecialty(source.getSubspecialty());
        target.setDepartment(source.getDepartment());
        target.setEmail(source.getEmail());
        target.setPhoneOffice(source.getPhoneOffice());
        target.setPhoneMobile(source.getPhoneMobile());
        target.setPager(source.getPager());
        target.setFax(source.getFax());
        if (target.getLicenses() == null) {
            target.setLicenses(new HashSet<>());
        }
        target.getLicenses().clear();
        if (source.getLicenses() != null) {
            target.getLicenses().addAll(source.getLicenses());
        }
        target.setHireDate(source.getHireDate());
        target.setTerminationDate(source.getTerminationDate());
        target.setAcceptingPatients(source.getAcceptingPatients() == null || source.getAcceptingPatients());
    }

    @Transactional(readOnly = true)
    public List<Provider> search(String lastName) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName);
    }
}
