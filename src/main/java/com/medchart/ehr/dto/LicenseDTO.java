package com.medchart.ehr.dto;

import lombok.*;

import javax.validation.constraints.Size;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseDTO {

    @Size(max = 50)
    private String licenseType;

    @Size(max = 50)
    private String licenseNumber;

    @Size(max = 50)
    private String state;

    private LocalDate issueDate;

    private LocalDate expirationDate;

    @Size(max = 20)
    private String status;
}
