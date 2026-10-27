package com.medchart.ehr.domain.encounter;

import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "encounters", indexes = {
    @Index(name = "idx_encounter_patient", columnList = "patient_id"),
    @Index(name = "idx_encounter_provider", columnList = "attending_provider_id"),
    @Index(name = "idx_encounter_date", columnList = "encounterDateTime"),
    @Index(name = "idx_encounter_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String encounterNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attending_provider_id")
    private Provider attendingProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EncounterType encounterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EncounterStatus status;

    @Column(nullable = false)
    private LocalDateTime encounterDateTime;

    private LocalDateTime admitDateTime;

    private LocalDateTime dischargeDateTime;

    @Column(length = 50)
    private String department;

    @Column(length = 20)
    private String room;

    @Column(length = 20)
    private String bed;

    @Column(length = 500)
    private String chiefComplaint;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EncounterPriority priority;

    @Column(length = 20)
    private String visitType;

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL)
    @Builder.Default
    private List<EncounterDiagnosis> diagnoses = new ArrayList<>();

    @Column(length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Version
    private Long version;

    public void addDiagnosis(EncounterDiagnosis diagnosis) {
        diagnoses.add(diagnosis);
        diagnosis.setEncounter(this);
    }
}
