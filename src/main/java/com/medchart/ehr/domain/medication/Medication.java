package com.medchart.ehr.domain.medication;

import lombok.*;

import jakarta.persistence.*;

@Entity
@Table(name = "medications", indexes = {
    @Index(name = "idx_medication_ndc", columnList = "ndcCode"),
    @Index(name = "idx_medication_rxnorm", columnList = "rxnormCode"),
    @Index(name = "idx_medication_name", columnList = "genericName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String ndcCode;

    @Column(length = 20)
    private String rxnormCode;

    @Column(nullable = false, length = 200)
    private String genericName;

    @Column(length = 200)
    private String brandName;

    @Column(length = 100)
    private String manufacturer;

    @Column(length = 100)
    private String strength;

    @Column(length = 100)
    private String form;

    @Column(length = 100)
    private String route;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrugSchedule schedule;

    @Column(nullable = false)
    @Builder.Default
    private Boolean controlled = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(length = 1000)
    private String warnings;

    @Column(length = 500)
    private String contraindications;
}
