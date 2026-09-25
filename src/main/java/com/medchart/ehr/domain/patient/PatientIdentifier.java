package com.medchart.ehr.domain.patient;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "patient_identifiers", indexes = {
    @Index(name = "idx_patient_identifier_value", columnList = "identifierValue"),
    @Index(name = "idx_patient_identifier_type", columnList = "identifierType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IdentifierType identifierType;

    @Column(nullable = false, length = 100)
    private String identifierValue;

    @Column(length = 100)
    private String issuingAuthority;

    private LocalDate effectiveDate;

    private LocalDate expirationDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
