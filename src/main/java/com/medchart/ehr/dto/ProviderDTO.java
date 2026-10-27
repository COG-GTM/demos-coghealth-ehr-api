package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Write model for provider create/update. Server-managed fields (id, version,
 * createdAt, updatedAt, active) are intentionally not bindable.
 */
@Data
@NoArgsConstructor
public class ProviderDTO {

    @NotBlank(message = "NPI is required")
    @Size(max = 10)
    private String npi;

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String middleName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String suffix;

    @Size(max = 50)
    private String credentials;

    @NotNull(message = "Provider type is required")
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

    private Set<License> licenses;

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
            provider.getLicenses().addAll(new HashSet<>(licenses));
        }
    }
}
