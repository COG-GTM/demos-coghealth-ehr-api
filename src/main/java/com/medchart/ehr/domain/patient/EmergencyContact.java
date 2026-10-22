package com.medchart.ehr.domain.patient;

import lombok.*;

import jakarta.persistence.*;

@Entity
@Table(name = "emergency_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 50)
    private String relationship;

    @Column(length = 20)
    private String phoneHome;

    @Column(length = 20)
    private String phoneMobile;

    @Column(length = 20)
    private String phoneWork;

    @Column(length = 100)
    private String email;

    @Embedded
    private Address address;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 1;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
