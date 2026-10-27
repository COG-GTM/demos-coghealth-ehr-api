package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.LicenseDTO;
import com.medchart.ehr.dto.ProviderCreateRequest;
import com.medchart.ehr.dto.ProviderResponse;
import com.medchart.ehr.dto.ProviderUpdateRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Explicit field allowlist between provider API DTOs and the JPA entity.
 * Written by hand so a field added to the entity is never client-writable by default.
 */
@Component
public class ProviderMapper {

    public ProviderResponse toResponse(Provider provider) {
        return ProviderResponse.builder()
                .id(provider.getId())
                .npi(provider.getNpi())
                .firstName(provider.getFirstName())
                .middleName(provider.getMiddleName())
                .lastName(provider.getLastName())
                .suffix(provider.getSuffix())
                .credentials(provider.getCredentials())
                .fullName(provider.getFullName())
                .displayName(provider.getDisplayName())
                .providerType(provider.getProviderType())
                .specialty(provider.getSpecialty())
                .subspecialty(provider.getSubspecialty())
                .department(provider.getDepartment())
                .email(provider.getEmail())
                .phoneOffice(provider.getPhoneOffice())
                .phoneMobile(provider.getPhoneMobile())
                .pager(provider.getPager())
                .fax(provider.getFax())
                .licenses(toLicenseDtos(provider.getLicenses()))
                .hireDate(provider.getHireDate())
                .terminationDate(provider.getTerminationDate())
                .active(provider.getActive())
                .acceptingPatients(provider.getAcceptingPatients())
                .build();
    }

    public List<ProviderResponse> toResponseList(Collection<Provider> providers) {
        return providers.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Provider toEntity(ProviderCreateRequest request) {
        Provider provider = Provider.builder()
                .npi(request.getNpi())
                .firstName(request.getFirstName())
                .middleName(request.getMiddleName())
                .lastName(request.getLastName())
                .suffix(request.getSuffix())
                .credentials(request.getCredentials())
                .providerType(request.getProviderType())
                .specialty(request.getSpecialty())
                .subspecialty(request.getSubspecialty())
                .department(request.getDepartment())
                .email(request.getEmail())
                .phoneOffice(request.getPhoneOffice())
                .phoneMobile(request.getPhoneMobile())
                .pager(request.getPager())
                .fax(request.getFax())
                .hireDate(request.getHireDate())
                .build();
        if (request.getLicenses() != null) {
            provider.setLicenses(toLicenses(request.getLicenses()));
        }
        if (request.getAcceptingPatients() != null) {
            provider.setAcceptingPatients(request.getAcceptingPatients());
        }
        return provider;
    }

    public void applyUpdate(ProviderUpdateRequest request, Provider provider) {
        setIfPresent(request.getNpi(), provider::setNpi);
        setIfPresent(request.getFirstName(), provider::setFirstName);
        setIfPresent(request.getMiddleName(), provider::setMiddleName);
        setIfPresent(request.getLastName(), provider::setLastName);
        setIfPresent(request.getSuffix(), provider::setSuffix);
        setIfPresent(request.getCredentials(), provider::setCredentials);
        setIfPresent(request.getProviderType(), provider::setProviderType);
        setIfPresent(request.getSpecialty(), provider::setSpecialty);
        setIfPresent(request.getSubspecialty(), provider::setSubspecialty);
        setIfPresent(request.getDepartment(), provider::setDepartment);
        setIfPresent(request.getEmail(), provider::setEmail);
        setIfPresent(request.getPhoneOffice(), provider::setPhoneOffice);
        setIfPresent(request.getPhoneMobile(), provider::setPhoneMobile);
        setIfPresent(request.getPager(), provider::setPager);
        setIfPresent(request.getFax(), provider::setFax);
        setIfPresent(request.getHireDate(), provider::setHireDate);
        setIfPresent(request.getTerminationDate(), provider::setTerminationDate);
        setIfPresent(request.getActive(), provider::setActive);
        setIfPresent(request.getAcceptingPatients(), provider::setAcceptingPatients);
        if (request.getLicenses() != null) {
            provider.getLicenses().clear();
            provider.getLicenses().addAll(toLicenses(request.getLicenses()));
        }
    }

    private static <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private List<LicenseDTO> toLicenseDtos(Collection<License> licenses) {
        if (licenses == null) {
            return List.of();
        }
        return licenses.stream()
                .map(l -> LicenseDTO.builder()
                        .licenseType(l.getLicenseType())
                        .licenseNumber(l.getLicenseNumber())
                        .state(l.getState())
                        .issueDate(l.getIssueDate())
                        .expirationDate(l.getExpirationDate())
                        .status(l.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    private Set<License> toLicenses(List<LicenseDTO> dtos) {
        return dtos.stream()
                .map(d -> License.builder()
                        .licenseType(d.getLicenseType())
                        .licenseNumber(d.getLicenseNumber())
                        .state(d.getState())
                        .issueDate(d.getIssueDate())
                        .expirationDate(d.getExpirationDate())
                        .status(d.getStatus())
                        .build())
                .collect(Collectors.toCollection(java.util.HashSet::new));
    }
}
