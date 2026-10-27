package com.medchart.ehr.dto;

import com.medchart.ehr.domain.provider.ProviderType;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Partial update of a provider. Null fields are left unchanged on the stored
 * record; id, version and timestamps cannot be set by clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderUpdateRequest {

    private static final String NOT_BLANK = ".*\\S.*";

    @Pattern(regexp = "\\d{10}", message = "must be a 10-digit NPI")
    private String npi;

    @Pattern(regexp = NOT_BLANK, message = "must not be blank")
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String middleName;

    @Pattern(regexp = NOT_BLANK, message = "must not be blank")
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String suffix;

    @Size(max = 50)
    private String credentials;

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
    @Size(max = 25)
    private List<LicenseDTO> licenses;

    private LocalDate hireDate;

    private LocalDate terminationDate;

    private Boolean active;

    private Boolean acceptingPatients;
}
