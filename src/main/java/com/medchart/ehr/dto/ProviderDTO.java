package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.ProviderType;
import lombok.*;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writable provider fields. Server-managed fields (id, version, active, createdAt, updatedAt)
 * are deliberately absent; any property not declared here makes the request invalid.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderDTO {

    @NotBlank(message = "NPI is required")
    @Pattern(regexp = "\\d{10}", message = "NPI must be 10 digits")
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

    @Builder.Default
    private Set<License> licenses = new HashSet<>();

    private LocalDate hireDate;

    private LocalDate terminationDate;

    @Builder.Default
    private Boolean acceptingPatients = true;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Builder.Default
    private Set<String> unexpectedFields = new TreeSet<>();

    @JsonAnySetter
    public void rejectUnexpectedField(String name, Object value) {
        unexpectedFields.add(name);
    }

    @JsonIgnore
    public Set<String> getUnexpectedFields() {
        return Collections.unmodifiableSet(unexpectedFields);
    }

    @JsonIgnore
    @AssertTrue(message = "Request contains fields that cannot be set (e.g. id, version, active, createdAt, updatedAt)")
    public boolean isOnlyWritableFields() {
        return unexpectedFields.isEmpty();
    }
}
