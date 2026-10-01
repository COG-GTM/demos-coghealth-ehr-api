package com.medchart.ehr.export;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Decrypted export payload served to the client, together with the metadata
 * needed to set the correct content type and filename.
 */
@Getter
@RequiredArgsConstructor
public class ExportDownload {

    private final byte[] content;
    private final ExportFormat format;
    private final String exportReference;

    public String getFileName() {
        String extension = format == ExportFormat.JSON ? ".json" : ".csv";
        return "export_" + exportReference + extension;
    }

    public String getContentType() {
        return format == ExportFormat.JSON ? "application/json" : "text/csv";
    }
}
