package com.medchart.ehr.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/v1/exports")
@RequiredArgsConstructor
@Tag(name = "Patient Export", description = "HIPAA-compliant batch patient data export")
public class PatientExportController {

    private final BatchPatientExportService exportService;

    @PostMapping
    @Operation(summary = "Create a batch patient data export")
    public ResponseEntity<DataExportDTO> createExport(
            @Valid @RequestBody BatchExportRequest request,
            HttpServletRequest httpRequest) {

        String userId = getCurrentUserId();
        String userName = getCurrentUserName();
        String ipAddress = getClientIpAddress(httpRequest);

        DataExportDTO result = exportService.createExport(request, userId, userName, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{exportReference}/download")
    @Operation(summary = "Download an export file (decrypted, served over TLS)")
    public ResponseEntity<byte[]> downloadExport(
            @PathVariable String exportReference,
            HttpServletRequest httpRequest) {

        String userId = getCurrentUserId();
        String userName = getCurrentUserName();
        String ipAddress = getClientIpAddress(httpRequest);

        ExportDownload download = exportService.downloadExport(
                exportReference, userId, userName, isCurrentUserAdmin(), ipAddress);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + download.getFileName())
                .contentType(MediaType.parseMediaType(download.getContentType()))
                .contentLength(download.getContent().length)
                .body(download.getContent());
    }

    @GetMapping
    @Operation(summary = "Get export history with download counts (admins see all)")
    public ResponseEntity<Page<DataExportDTO>> getExportHistory(Pageable pageable) {
        return ResponseEntity.ok(
                exportService.getExportHistory(getCurrentUserId(), isCurrentUserAdmin(), pageable));
    }

    @ExceptionHandler(ExportAccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(ExportAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(ExportException.class)
    public ResponseEntity<String> handleExportException(ExportException e) {
        return ResponseEntity.status(e.getStatus()).body(e.getMessage());
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }

    private String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "System User";
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
