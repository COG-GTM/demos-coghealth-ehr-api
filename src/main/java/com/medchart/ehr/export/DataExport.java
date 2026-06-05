package com.medchart.ehr.export;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_exports", indexes = {
    @Index(name = "idx_export_reference", columnList = "exportReference"),
    @Index(name = "idx_export_user", columnList = "userId"),
    @Index(name = "idx_export_created", columnList = "createdAt"),
    @Index(name = "idx_export_expires", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 36)
    private String exportReference;

    @Column(nullable = false, length = 100)
    private String userId;

    @Column(length = 100)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExportReason reason;

    @Column(length = 500)
    private String reasonDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExportFormat format;

    @Column(nullable = false)
    private Integer patientCount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deIdentified = false;

    @Column(length = 500)
    private String filePath;

    private Long fileSizeBytes;

    @Column(nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(length = 50)
    private String ipAddress;

    public void incrementDownloadCount() {
        this.downloadCount++;
    }
}
