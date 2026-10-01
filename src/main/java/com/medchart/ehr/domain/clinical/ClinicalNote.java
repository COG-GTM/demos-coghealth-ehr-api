package com.medchart.ehr.domain.clinical;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_notes", indexes = {
    @Index(name = "idx_note_patient", columnList = "patient_id"),
    @Index(name = "idx_note_encounter", columnList = "encounter_id"),
    @Index(name = "idx_note_author", columnList = "author_id"),
    @Index(name = "idx_note_type", columnList = "noteType")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encounter_id")
    private Encounter encounter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private Provider author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NoteType noteType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteStatus status;

    private LocalDateTime signedDateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cosigner_id")
    private Provider cosigner;

    private LocalDateTime cosignedDateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amended_from_id")
    private ClinicalNote amendedFrom;

    @Column(length = 500)
    private String amendmentReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public boolean isSigned() {
        return signedDateTime != null;
    }

    public boolean requiresCosign() {
        return cosigner != null && cosignedDateTime == null;
    }
}
