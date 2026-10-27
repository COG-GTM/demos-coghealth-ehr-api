package com.medchart.ehr.dto;

import com.medchart.ehr.domain.provider.ProviderType;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderResponse {
    private Long id;
    private String npi;
    private String firstName;
    private String middleName;
    private String lastName;
    private String suffix;
    private String credentials;
    private String fullName;
    private String displayName;
    private ProviderType providerType;
    private String specialty;
    private String subspecialty;
    private String department;
    private String email;
    private String phoneOffice;
    private String phoneMobile;
    private String pager;
    private String fax;
    private List<LicenseDTO> licenses;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private Boolean active;
    private Boolean acceptingPatients;
}
