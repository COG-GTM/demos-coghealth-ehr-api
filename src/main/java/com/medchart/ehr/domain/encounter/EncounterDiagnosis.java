package com.medchart.ehr.domain.encounter;

import com.medchart.ehr.domain.clinical.Diagnosis;
import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "encounter_diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id", nullable = false)
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diagnosis_id", nullable = false)
    private Diagnosis diagnosis;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiagnosisRole role;

    @Column(nullable = false)
    @Builder.Default
    private Integer rank = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean primaryDiagnosis = false;
}
