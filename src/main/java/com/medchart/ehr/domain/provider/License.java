package com.medchart.ehr.domain.provider;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License implements Serializable {

    private static final long serialVersionUID = 1L;

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
