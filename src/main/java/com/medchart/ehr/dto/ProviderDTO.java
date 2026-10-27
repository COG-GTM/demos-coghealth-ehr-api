package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    /** Optional fields the request explicitly set to null, so an update can clear them. */
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final Set<String> explicitNulls = new HashSet<>();

    @JsonProperty("id")
    public void markIdSupplied(Object id) {
        this.idSupplied = id != null;
    }

    public void setMiddleName(String middleName) {
        this.middleName = track("middleName", middleName);
    }

    public void setSuffix(String suffix) {
        this.suffix = track("suffix", suffix);
    }

    public void setCredentials(String credentials) {
        this.credentials = track("credentials", credentials);
    }

    public void setSpecialty(String specialty) {
        this.specialty = track("specialty", specialty);
    }

    public void setSubspecialty(String subspecialty) {
        this.subspecialty = track("subspecialty", subspecialty);
    }

    public void setDepartment(String department) {
        this.department = track("department", department);
    }

    public void setEmail(String email) {
        this.email = track("email", email);
    }

    public void setPhoneOffice(String phoneOffice) {
        this.phoneOffice = track("phoneOffice", phoneOffice);
    }

    public void setPhoneMobile(String phoneMobile) {
        this.phoneMobile = track("phoneMobile", phoneMobile);
    }

    public void setPager(String pager) {
        this.pager = track("pager", pager);
    }

    public void setFax(String fax) {
        this.fax = track("fax", fax);
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = track("hireDate", hireDate);
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = track("terminationDate", terminationDate);
    }

    private <T> T track(String field, T value) {
        if (value == null) {
            explicitNulls.add(field);
        } else {
            explicitNulls.remove(field);
        }
        return value;
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
     * omitted fields keep their stored values and an explicit null clears an optional field.
     * Required fields ignore null, and the NPI is never changed by an update.
     */
    public void applyUpdateTo(Provider provider) {
        setIfPresent(firstName, provider::setFirstName);
        setOrClear("middleName", middleName, provider::setMiddleName);
        setIfPresent(lastName, provider::setLastName);
        setOrClear("suffix", suffix, provider::setSuffix);
        setOrClear("credentials", credentials, provider::setCredentials);
        setIfPresent(providerType, provider::setProviderType);
        setOrClear("specialty", specialty, provider::setSpecialty);
        setOrClear("subspecialty", subspecialty, provider::setSubspecialty);
        setOrClear("department", department, provider::setDepartment);
        setOrClear("email", email, provider::setEmail);
        setOrClear("phoneOffice", phoneOffice, provider::setPhoneOffice);
        setOrClear("phoneMobile", phoneMobile, provider::setPhoneMobile);
        setOrClear("pager", pager, provider::setPager);
        setOrClear("fax", fax, provider::setFax);
        setOrClear("hireDate", hireDate, provider::setHireDate);
        setOrClear("terminationDate", terminationDate, provider::setTerminationDate);
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

    private <T> void setOrClear(String field, T value, Consumer<T> setter) {
        if (value != null || explicitNulls.contains(field)) {
            setter.accept(value);
        }
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
