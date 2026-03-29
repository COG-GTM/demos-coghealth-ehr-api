package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
    @Cacheable(value = "allProviders")
    public List<Provider> findAll() {
        return providerRepository.findAll();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "activeProviders")
    public List<Provider> findActive() {
        return providerRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "providers", key = "#id")
    public Optional<Provider> findById(Long id) {
        return providerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "providersByNpi", key = "#npi")
    public Optional<Provider> findByNpi(String npi) {
        return providerRepository.findByNpi(npi);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "providersByDepartment", key = "#department")
    public List<Provider> findByDepartment(String department) {
        return providerRepository.findByDepartment(department);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "providersBySpecialty", key = "#specialty")
    public List<Provider> findBySpecialty(String specialty) {
        return providerRepository.findBySpecialty(specialty);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "departments")
    public List<String> getAllDepartments() {
        return providerRepository.findAllDepartments();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "specialties")
    public List<String> getAllSpecialties() {
        return providerRepository.findAllSpecialties();
    }

    @Caching(evict = {
            @CacheEvict(value = "allProviders", allEntries = true),
            @CacheEvict(value = "activeProviders", allEntries = true),
            @CacheEvict(value = "providersByDepartment", allEntries = true),
            @CacheEvict(value = "providersBySpecialty", allEntries = true),
            @CacheEvict(value = "departments", allEntries = true),
            @CacheEvict(value = "specialties", allEntries = true)
    })
    public Provider save(Provider provider) {
        log.info("Saving provider: {} {}", provider.getFirstName(), provider.getLastName());
        return providerRepository.save(provider);
    }

    @Caching(evict = {
            @CacheEvict(value = "providers", key = "#id"),
            @CacheEvict(value = "allProviders", allEntries = true),
            @CacheEvict(value = "activeProviders", allEntries = true),
            @CacheEvict(value = "providersByDepartment", allEntries = true),
            @CacheEvict(value = "providersBySpecialty", allEntries = true)
    })
    public void deactivate(Long id) {
        providerRepository.findById(id).ifPresent(provider -> {
            provider.setActive(false);
            providerRepository.save(provider);
            log.info("Deactivated provider: {}", provider.getNpi());
        });
    }

    @Transactional(readOnly = true)
    public List<Provider> search(String lastName) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName);
    }
}
