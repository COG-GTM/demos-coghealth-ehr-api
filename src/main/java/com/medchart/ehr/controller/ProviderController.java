package com.medchart.ehr.controller;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.service.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for healthcare provider management.
 *
 * Supports lookup by NPI, department, and specialty, as well as
 * CRUD operations and provider deactivation.
 */
@RestController
@RequestMapping("/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<Provider> getAll(@RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return providerService.findActive();
        }
        return providerService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Provider> getById(@PathVariable Long id) {
        return providerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/npi/{npi}")
    public ResponseEntity<Provider> getByNpi(@PathVariable String npi) {
        return providerService.findByNpi(npi)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/department/{department}")
    public List<Provider> getByDepartment(@PathVariable String department) {
        return providerService.findByDepartment(department);
    }

    @GetMapping("/specialty/{specialty}")
    public List<Provider> getBySpecialty(@PathVariable String specialty) {
        return providerService.findBySpecialty(specialty);
    }

    @GetMapping("/departments")
    public List<String> getDepartments() {
        return providerService.getAllDepartments();
    }

    @GetMapping("/specialties")
    public List<String> getSpecialties() {
        return providerService.getAllSpecialties();
    }

    @GetMapping("/search")
    public List<Provider> search(@RequestParam String lastName) {
        return providerService.search(lastName);
    }

    @PostMapping
    public Provider create(@RequestBody Provider provider) {
        return providerService.save(provider);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Provider> update(@PathVariable Long id, @RequestBody Provider provider) {
        return providerService.findById(id)
                .map(existing -> {
                    provider.setId(id);
                    return ResponseEntity.ok(providerService.save(provider));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        providerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
