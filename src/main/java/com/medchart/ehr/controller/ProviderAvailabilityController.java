package com.medchart.ehr.controller;

import com.medchart.ehr.domain.provider.ProviderAvailability;
import com.medchart.ehr.dto.ProviderAvailabilityDTO;
import com.medchart.ehr.service.ProviderAvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/providers/{providerId}/availability")
public class ProviderAvailabilityController {

    private final ProviderAvailabilityService availabilityService;

    public ProviderAvailabilityController(ProviderAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<ProviderAvailability> getAll(@PathVariable Long providerId) {
        return availabilityService.findByProvider(providerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderAvailability> getById(@PathVariable Long providerId, @PathVariable Long id) {
        return availabilityService.findById(id)
                .filter(a -> a.getProvider().getId().equals(providerId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/recurring")
    public List<ProviderAvailability> getRecurring(@PathVariable Long providerId) {
        return availabilityService.findRecurring(providerId);
    }

    @GetMapping("/date/{date}")
    public List<ProviderAvailability> getByDate(
            @PathVariable Long providerId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availabilityService.findByDate(providerId, date);
    }

    @GetMapping("/effective")
    public List<ProviderAvailability> getEffective(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return availabilityService.findEffectiveForDate(providerId, date);
    }

    @GetMapping("/range")
    public List<ProviderAvailability> getByDateRange(
            @PathVariable Long providerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availabilityService.findByDateRange(providerId, startDate, endDate);
    }

    @PostMapping
    public ResponseEntity<ProviderAvailability> create(
            @PathVariable Long providerId,
            @Valid @RequestBody ProviderAvailabilityDTO dto) {
        dto.setProviderId(providerId);
        ProviderAvailability created = availabilityService.create(providerId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProviderAvailability> update(
            @PathVariable Long providerId,
            @PathVariable Long id,
            @Valid @RequestBody ProviderAvailabilityDTO dto) {
        return availabilityService.findById(id)
                .filter(a -> a.getProvider().getId().equals(providerId))
                .map(existing -> {
                    dto.setProviderId(providerId);
                    return ResponseEntity.ok(availabilityService.update(id, dto));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long providerId,
            @PathVariable Long id) {
        return availabilityService.findById(id)
                .filter(a -> a.getProvider().getId().equals(providerId))
                .map(existing -> {
                    availabilityService.deactivate(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
