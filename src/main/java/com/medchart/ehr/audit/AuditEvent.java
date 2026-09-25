package com.medchart.ehr.audit;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_user", columnList = "userId"),
    @Index(name = "idx_audit_patient", columnList = "patientId"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(length = 100)
    private String userName;

    private Long patientId;

    @Column(length = 20)
    private String patientMrn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(nullable = false, length = 100)
    private String resourceType;

    private Long resourceId;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 200)
    private String userAgent;

    @Column(length = 100)
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String requestDetails;

    @Column(columnDefinition = "TEXT")
    private String responseDetails;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime timestamp;
}
