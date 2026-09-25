package com.medchart.ehr.domain.provider;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "providers", indexes = {
    @Index(name = "idx_provider_npi", columnList = "npi"),
    @Index(name = "idx_provider_last_name", columnList = "lastName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 10)
    private String npi;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String middleName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String suffix;

    @Column(length = 50)
    private String credentials;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProviderType providerType;

    @Column(length = 100)
    private String specialty;

    @Column(length = 100)
    private String subspecialty;

    @Column(length = 50)
    private String department;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phoneOffice;

    @Column(length = 20)
    private String phoneMobile;

    @Column(length = 20)
    private String pager;

    @Column(length = 20)
    private String fax;

    @ElementCollection
    @CollectionTable(name = "provider_licenses", joinColumns = @JoinColumn(name = "provider_id"))
    @Builder.Default
    private Set<License> licenses = new HashSet<>();

    private LocalDate hireDate;

    private LocalDate terminationDate;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean acceptingPatients = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        sb.append(firstName);
        if (middleName != null) sb.append(" ").append(middleName);
        sb.append(" ").append(lastName);
        if (suffix != null) sb.append(", ").append(suffix);
        if (credentials != null) sb.append(", ").append(credentials);
        return sb.toString();
    }

    public String getDisplayName() {
        return lastName + ", " + firstName + (credentials != null ? ", " + credentials : "");
    }
}
