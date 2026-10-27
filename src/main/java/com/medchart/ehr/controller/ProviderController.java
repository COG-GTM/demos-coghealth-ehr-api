package com.medchart.ehr.controller;

import com.medchart.ehr.dto.ProviderCreateRequest;
import com.medchart.ehr.dto.ProviderResponse;
import com.medchart.ehr.dto.ProviderUpdateRequest;
import com.medchart.ehr.service.ProviderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<ProviderResponse> getAll(@RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return providerService.findActive();
        }
        return providerService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderResponse> getById(@PathVariable Long id) {
        return providerService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/npi/{npi}")
    public ResponseEntity<ProviderResponse> getByNpi(@PathVariable String npi) {
        return providerService.findByNpi(npi)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/department/{department}")
    public List<ProviderResponse> getByDepartment(@PathVariable String department) {
        return providerService.findByDepartment(department);
    }

    @GetMapping("/specialty/{specialty}")
    public List<ProviderResponse> getBySpecialty(@PathVariable String specialty) {
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
    public List<ProviderResponse> search(@RequestParam String lastName) {
        return providerService.search(lastName);
    }

    @PostMapping
    public ProviderResponse create(@Valid @RequestBody ProviderCreateRequest request) {
        return providerService.create(request);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProviderResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody ProviderUpdateRequest request) {
        return providerService.update(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        providerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
