package com.medchart.ehr.domain.patient;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "patients", indexes = {
    @Index(name = "idx_patient_mrn", columnList = "mrn"),
    @Index(name = "idx_patient_ssn", columnList = "ssn"),
    @Index(name = "idx_patient_last_name", columnList = "lastName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String mrn;

    @Column(length = 11)
    private String ssn;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String middleName;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String lastName;

    @Past
    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private MaritalStatus maritalStatus;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phoneHome;

    @Column(length = 20)
    private String phoneMobile;

    @Column(length = 20)
    private String phoneWork;

    @Embedded
    private Address address;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "street1", column = @Column(name = "mail_street1")),
        @AttributeOverride(name = "street2", column = @Column(name = "mail_street2")),
        @AttributeOverride(name = "city", column = @Column(name = "mail_city")),
        @AttributeOverride(name = "state", column = @Column(name = "mail_state")),
        @AttributeOverride(name = "zipCode", column = @Column(name = "mail_zip_code")),
        @AttributeOverride(name = "country", column = @Column(name = "mail_country"))
    })
    private Address mailingAddress;

    @Column(length = 50)
    private String preferredLanguage;

    @Column(length = 50)
    private String ethnicity;

    @Column(length = 50)
    private String race;

    @Column(length = 100)
    private String religion;

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PatientIdentifier> identifiers = new ArrayList<>();

    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EmergencyContact> emergencyContacts = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "deceased")
    @Builder.Default
    private Boolean deceased = false;

    @Column(name = "deceased_date")
    private LocalDateTime deceasedDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    private Long version;

    public String getFullName() {
        if (middleName != null && !middleName.isBlank()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }

    public void addIdentifier(PatientIdentifier identifier) {
        identifiers.add(identifier);
        identifier.setPatient(this);
    }

    public void removeIdentifier(PatientIdentifier identifier) {
        identifiers.remove(identifier);
        identifier.setPatient(null);
    }

    public void addEmergencyContact(EmergencyContact contact) {
        emergencyContacts.add(contact);
        contact.setPatient(this);
    }
}
