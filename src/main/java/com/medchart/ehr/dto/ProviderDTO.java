package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.groups.Default;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Write model for provider create/update. Server-managed fields (id, version,
 * createdAt, updatedAt, active) are intentionally not bindable, and the NPI is
 * fixed once a provider exists.
 */
@Data
@NoArgsConstructor
public class ProviderDTO {

    /** Validation group for create: required fields must be present. */
    public interface OnCreate extends Default {
    }

    @NotBlank(message = "NPI is required", groups = OnCreate.class)
    @Pattern(regexp = "\\d{10}", message = "NPI must be 10 digits")
    private String npi;

    @NotBlank(message = "First name is required", groups = OnCreate.class)
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String middleName;

    @NotBlank(message = "Last name is required", groups = OnCreate.class)
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String suffix;

    @Size(max = 50)
    private String credentials;

    @NotNull(message = "Provider type is required", groups = OnCreate.class)
    private ProviderType providerType;

    @Size(max = 100)
    private String specialty;

    @Size(max = 100)
    private String subspecialty;

    @Size(max = 50)
    private String department;

    @Email
    @Size(max = 100)
    private String email;

    @Size(max = 20)
    private String phoneOffice;

    @Size(max = 20)
    private String phoneMobile;

    @Size(max = 20)
    private String pager;

    @Size(max = 20)
    private String fax;

    @Valid
    private Set<LicenseDTO> licenses;

    private LocalDate hireDate;

    private LocalDate terminationDate;

    private Boolean acceptingPatients;

    /** Optimistic-locking precondition for updates; never copied onto the entity. */
    @JsonProperty("version")
    private Long expectedVersion;

    @JsonIgnore
    private boolean idSupplied;

    @JsonProperty("id")
    public void markIdSupplied(Object id) {
        this.idSupplied = id != null;
    }

    /** Populates a new provider; every editable field, including the NPI, is taken from the request. */
    public void applyTo(Provider provider) {
        provider.setNpi(npi);
        provider.setFirstName(firstName);
        provider.setMiddleName(middleName);
        provider.setLastName(lastName);
        provider.setSuffix(suffix);
        provider.setCredentials(credentials);
        provider.setProviderType(providerType);
        provider.setSpecialty(specialty);
        provider.setSubspecialty(subspecialty);
        provider.setDepartment(department);
        provider.setEmail(email);
        provider.setPhoneOffice(phoneOffice);
        provider.setPhoneMobile(phoneMobile);
        provider.setPager(pager);
        provider.setFax(fax);
        provider.setHireDate(hireDate);
        provider.setTerminationDate(terminationDate);
        if (acceptingPatients != null) {
            provider.setAcceptingPatients(acceptingPatients);
        }
        if (licenses != null) {
            provider.getLicenses().clear();
            provider.getLicenses().addAll(toLicenses());
        }
    }

    /**
     * Applies an update to a loaded provider. Only fields present in the request are copied;
     * omitted fields keep their stored values. The NPI is never changed by an update.
     */
    public void applyUpdateTo(Provider provider) {
        setIfPresent(firstName, provider::setFirstName);
        setIfPresent(middleName, provider::setMiddleName);
        setIfPresent(lastName, provider::setLastName);
        setIfPresent(suffix, provider::setSuffix);
        setIfPresent(credentials, provider::setCredentials);
        setIfPresent(providerType, provider::setProviderType);
        setIfPresent(specialty, provider::setSpecialty);
        setIfPresent(subspecialty, provider::setSubspecialty);
        setIfPresent(department, provider::setDepartment);
        setIfPresent(email, provider::setEmail);
        setIfPresent(phoneOffice, provider::setPhoneOffice);
        setIfPresent(phoneMobile, provider::setPhoneMobile);
        setIfPresent(pager, provider::setPager);
        setIfPresent(fax, provider::setFax);
        setIfPresent(hireDate, provider::setHireDate);
        setIfPresent(terminationDate, provider::setTerminationDate);
        setIfPresent(acceptingPatients, provider::setAcceptingPatients);
        if (licenses != null) {
            provider.getLicenses().clear();
            provider.getLicenses().addAll(toLicenses());
        }
    }

    private Set<License> toLicenses() {
        Set<License> result = new HashSet<>();
        for (LicenseDTO license : licenses) {
            if (license != null) {
                result.add(license.toLicense());
            }
        }
        return result;
    }

    private static <T> void setIfPresent(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    @Data
    @NoArgsConstructor
    public static class LicenseDTO {

        @NotBlank
        @Size(max = 50)
        private String licenseType;

        @NotBlank
        @Size(max = 50)
        private String licenseNumber;

        @NotBlank
        @Size(max = 50)
        private String state;

        private LocalDate issueDate;

        private LocalDate expirationDate;

        @Size(max = 20)
        private String status;

        License toLicense() {
            return License.builder()
                    .licenseType(licenseType)
                    .licenseNumber(licenseNumber)
                    .state(state)
                    .issueDate(issueDate)
                    .expirationDate(expirationDate)
                    .status(status)
                    .build();
        }
    }
}
