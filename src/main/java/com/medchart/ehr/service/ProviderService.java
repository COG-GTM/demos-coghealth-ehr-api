package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class ProviderService {

    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderRepository providerRepository;

    @PersistenceContext
    private EntityManager entityManager;

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

    public Provider save(Provider provider) {
        log.info("Saving provider: {} {}", provider.getFirstName(), provider.getLastName());
        return providerRepository.save(provider);
    }

    public void deactivate(Long id) {
        providerRepository.findById(id).ifPresent(provider -> {
            provider.setActive(false);
            providerRepository.save(provider);
            log.info("Deactivated provider: {}", provider.getNpi());
        });
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Provider> search(String lastName) {
        String sql = "SELECT * FROM providers WHERE active = true "
                + "AND last_name ILIKE ?1 ESCAPE '\\' "
                + "ORDER BY last_name, first_name";
        Query query = entityManager.createNativeQuery(sql, Provider.class);
        query.setParameter(1, "%" + escapeLikePattern(lastName) + "%");
        return query.getResultList();
    }

    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
