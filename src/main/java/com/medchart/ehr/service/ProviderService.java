package com.medchart.ehr.service;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.ProviderCreateRequest;
import com.medchart.ehr.dto.ProviderResponse;
import com.medchart.ehr.dto.ProviderUpdateRequest;
import com.medchart.ehr.mapper.ProviderMapper;
import com.medchart.ehr.repository.ProviderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

    private static final String NPI_UNIQUE_CONSTRAINT = "providers_npi_key";
    private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public ProviderService(ProviderRepository providerRepository, ProviderMapper providerMapper) {
        this.providerRepository = providerRepository;
        this.providerMapper = providerMapper;
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findAll() {
        return providerMapper.toResponseList(providerRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findActive() {
        return providerMapper.toResponseList(providerRepository.findByActiveTrue());
    }

    @Transactional(readOnly = true)
    public Optional<ProviderResponse> findById(Long id) {
        return providerRepository.findById(id).map(providerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<ProviderResponse> findByNpi(String npi) {
        return providerRepository.findByNpi(npi).map(providerMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findByDepartment(String department) {
        return providerMapper.toResponseList(providerRepository.findByDepartment(department));
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> findBySpecialty(String specialty) {
        return providerMapper.toResponseList(providerRepository.findBySpecialty(specialty));
    }

    @Transactional(readOnly = true)
    public List<String> getAllDepartments() {
        return providerRepository.findAllDepartments();
    }

    @Transactional(readOnly = true)
    public List<String> getAllSpecialties() {
        return providerRepository.findAllSpecialties();
    }

    public ProviderResponse create(ProviderCreateRequest request) {
        if (providerRepository.findByNpi(request.getNpi()).isPresent()) {
            throw new DuplicateNpiException();
        }
        Provider saved = saveCheckingNpi(providerMapper.toEntity(request));
        log.info("Created provider id={}", saved.getId());
        return providerMapper.toResponse(saved);
    }

    public Optional<ProviderResponse> update(Long id, ProviderUpdateRequest request) {
        return providerRepository.findById(id).map(existing -> {
            if (request.getNpi() != null && !request.getNpi().equals(existing.getNpi())) {
                providerRepository.findByNpi(request.getNpi())
                        .filter(other -> !other.getId().equals(id))
                        .ifPresent(other -> {
                            throw new DuplicateNpiException();
                        });
            }
            providerMapper.applyUpdate(request, existing);
            Provider saved = saveCheckingNpi(existing);
            log.info("Updated provider id={}", saved.getId());
            return providerMapper.toResponse(saved);
        });
    }

    // The pre-checks above cover the common case; flushing here turns a concurrent
    // duplicate NPI insert into a 409 instead of a commit-time 500.
    private Provider saveCheckingNpi(Provider provider) {
        try {
            return providerRepository.saveAndFlush(provider);
        } catch (DataIntegrityViolationException e) {
            if (violatesNpiUniqueConstraint(e)) {
                throw new DuplicateNpiException();
            }
            throw e;
        }
    }

    private static boolean violatesNpiUniqueConstraint(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException
                    && NPI_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                            ((org.hibernate.exception.ConstraintViolationException) t).getConstraintName())) {
                return true;
            }
            if (t.getMessage() != null && t.getMessage().contains(NPI_UNIQUE_CONSTRAINT)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
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
    public List<ProviderResponse> search(String lastName) {
        String sql = "SELECT * FROM providers WHERE active = true "
                + "AND last_name ILIKE :pattern ESCAPE '\\' "
                + "ORDER BY last_name, first_name";
        Query query = entityManager.createNativeQuery(sql, Provider.class)
                .setParameter("pattern", "%" + escapeLike(lastName == null ? "" : lastName) + "%");
        return providerMapper.toResponseList(query.getResultList());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
