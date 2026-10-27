package com.medchart.ehr.dto;

import com.medchart.ehr.domain.provider.ProviderType;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Fields a client may set when creating a provider. Server-managed columns
 * (id, version, active, terminationDate, timestamps) are deliberately absent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderCreateRequest {

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "must be a 10-digit NPI")
    private String npi;

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String middleName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String suffix;

    @Size(max = 50)
    private String credentials;

    @NotNull
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

    private Boolean acceptingPatients;
}
