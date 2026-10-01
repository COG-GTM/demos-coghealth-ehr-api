package com.medchart.ehr.export;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataExportDTO {

    private String exportReference;
    private String userId;
    private String userName;
    private ExportReason reason;
    private String reasonDetails;
    private ExportFormat format;
    private Integer patientCount;
    private Boolean deIdentified;
    private Long fileSizeBytes;
    private Integer downloadCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean deleted;
    private String downloadUrl;

    public static DataExportDTO fromEntity(DataExport entity, String contextPath) {
        String prefix = contextPath == null ? "" : contextPath;
        return DataExportDTO.builder()
                .exportReference(entity.getExportReference())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .reason(entity.getReason())
                .reasonDetails(entity.getReasonDetails())
                .format(entity.getFormat())
                .patientCount(entity.getPatientCount())
                .deIdentified(entity.getDeIdentified())
                .fileSizeBytes(entity.getFileSizeBytes())
                .downloadCount(entity.getDownloadCount())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .deleted(entity.getDeleted())
                .downloadUrl(prefix + "/v1/exports/" + entity.getExportReference() + "/download")
                .build();
    }
}
