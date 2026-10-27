package com.medchart.ehr.controller;

import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.dto.ProviderResponseDTO;
import com.medchart.ehr.service.ProviderService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<ProviderResponseDTO> getAll(@RequestParam(required = false) Boolean active, Authentication auth) {
        if (Boolean.TRUE.equals(active)) {
            return toResponses(providerService.findActive(), auth);
        }
        return toResponses(providerService.findAll(), auth);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProviderResponseDTO> getById(@PathVariable Long id, Authentication auth) {
        return providerService.findById(id)
                .map(viewFor(auth))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/npi/{npi}")
    public ResponseEntity<ProviderResponseDTO> getByNpi(@PathVariable String npi, Authentication auth) {
        return providerService.findByNpi(npi)
                .map(viewFor(auth))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/department/{department}")
    public List<ProviderResponseDTO> getByDepartment(@PathVariable String department, Authentication auth) {
        return toResponses(providerService.findByDepartment(department), auth);
    }

    @GetMapping("/specialty/{specialty}")
    public List<ProviderResponseDTO> getBySpecialty(@PathVariable String specialty, Authentication auth) {
        return toResponses(providerService.findBySpecialty(specialty), auth);
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
    public List<ProviderResponseDTO> search(@RequestParam String lastName, Authentication auth) {
        return toResponses(providerService.search(lastName), auth);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProviderResponseDTO> create(@Validated(ProviderDTO.OnCreate.class) @RequestBody ProviderDTO provider) {
        if (provider.isIdSupplied()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ProviderResponseDTO.adminView(providerService.create(provider)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProviderResponseDTO> update(@PathVariable Long id, @Valid @RequestBody ProviderDTO provider) {
        try {
            return providerService.update(id, provider)
                    .map(ProviderResponseDTO::adminView)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (OptimisticLockingFailureException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        return providerService.deactivate(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static List<ProviderResponseDTO> toResponses(List<Provider> providers, Authentication auth) {
        return providers.stream().map(viewFor(auth)).collect(Collectors.toList());
    }

    private static Function<Provider, ProviderResponseDTO> viewFor(Authentication auth) {
        return isAdmin(auth) ? ProviderResponseDTO::adminView : ProviderResponseDTO::directoryView;
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
