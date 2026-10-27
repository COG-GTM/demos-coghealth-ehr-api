package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read model for provider responses. {@link #directoryView} is the staff directory
 * shape returned to every authenticated user; {@link #adminView} adds personal
 * contact numbers, license numbers and employment dates for administrators.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProviderResponseDTO {

    Long id;
    String npi;
    String firstName;
    String middleName;
    String lastName;
    String suffix;
    String credentials;
    String fullName;
    String displayName;
    ProviderType providerType;
    String specialty;
    String subspecialty;
    String department;
    String email;
    String phoneOffice;
    String fax;
    Boolean active;
    Boolean acceptingPatients;
    List<LicenseView> licenses;

    String phoneMobile;
    String pager;
    LocalDate hireDate;
    LocalDate terminationDate;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Long version;

    public static ProviderResponseDTO directoryView(Provider provider) {
        return base(provider, false).build();
    }

    public static ProviderResponseDTO adminView(Provider provider) {
        return base(provider, true)
                .phoneMobile(provider.getPhoneMobile())
                .pager(provider.getPager())
                .hireDate(provider.getHireDate())
                .terminationDate(provider.getTerminationDate())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .version(provider.getVersion())
                .build();
    }

    private static ProviderResponseDTOBuilder base(Provider provider, boolean includeLicenseNumbers) {
        List<LicenseView> licenses = provider.getLicenses() == null
                ? Collections.emptyList()
                : provider.getLicenses().stream()
                        .map(license -> LicenseView.of(license, includeLicenseNumbers))
                        .collect(Collectors.toList());
        return ProviderResponseDTO.builder()
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
                .fax(provider.getFax())
                .active(provider.getActive())
                .acceptingPatients(provider.getAcceptingPatients())
                .licenses(licenses);
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LicenseView {
        String licenseType;
        String licenseNumber;
        String state;
        LocalDate issueDate;
        LocalDate expirationDate;
        String status;

        static LicenseView of(License license, boolean includeNumber) {
            return LicenseView.builder()
                    .licenseType(license.getLicenseType())
                    .licenseNumber(includeNumber ? license.getLicenseNumber() : null)
                    .state(license.getState())
                    .issueDate(license.getIssueDate())
                    .expirationDate(license.getExpirationDate())
                    .status(license.getStatus())
                    .build();
        }
    }
}
