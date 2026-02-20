package com.medchart.ehr.domain.provider;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License {

    @Column(length = 50)
    private String licenseType;

    @Column(length = 50)
    private String licenseNumber;

    @Column(length = 50)
    private String state;

    private LocalDate issueDate;

    private LocalDate expirationDate;

    @Column(length = 20)
    private String status;
}
