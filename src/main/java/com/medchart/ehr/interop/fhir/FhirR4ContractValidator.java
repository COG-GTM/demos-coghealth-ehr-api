package com.medchart.ehr.interop.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates exported FHIR R4 JSON against the base R4 structure definitions (HAPI instance validator)
 * plus the identifier/reference rules the Epic Practitioner and Scheduling interfaces require.
 */
@Service
@Slf4j
public class FhirR4ContractValidator {

    private final FhirContext fhirContext;
    private final FhirValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    public FhirR4ContractValidator() {
        this.fhirContext = FhirContext.forR4();
        ValidationSupportChain chain = new ValidationSupportChain(
                new DefaultProfileValidationSupport(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext));
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(chain);
        instanceValidator.setNoTerminologyChecks(false);
        instanceValidator.setErrorForUnknownProfiles(false);
        this.validator = fhirContext.newValidator();
        this.validator.registerValidatorModule(instanceValidator);
    }

    public List<ContractFinding> validate(List<ExportedResource> resources) {
        List<ContractFinding> findings = new ArrayList<>();
        for (ExportedResource r : resources) {
            findings.addAll(validateStructure(r));
            findings.addAll(validateContractRules(r));
        }
        return findings;
    }

    List<ContractFinding> validateStructure(ExportedResource r) {
        List<ContractFinding> out = new ArrayList<>();
        ValidationResult result = validator.validateWithResult(r.getJson());
        for (SingleValidationMessage m : result.getMessages()) {
            String location = m.getLocationString() == null ? r.getResourceType() : m.getLocationString();
            out.add(new ContractFinding(r.getReference(), m.getSeverity().name(), location, m.getMessage()));
        }
        return out;
    }

    List<ContractFinding> validateContractRules(ExportedResource r) {
        List<ContractFinding> out = new ArrayList<>();
        JsonNode node;
        try {
            node = mapper.readTree(r.getJson());
        } catch (Exception e) {
            out.add(new ContractFinding(r.getReference(), "FATAL", r.getResourceType(), "Resource is not valid JSON: " + e.getMessage()));
            return out;
        }
        String type = r.getResourceType();
        if ("Practitioner".equals(type)) {
            boolean hasNpi = false;
            for (JsonNode id : node.path("identifier")) {
                if (FhirR4ExportService.NPI_SYSTEM.equals(id.path("system").asText(null))) {
                    hasNpi = true;
                }
            }
            if (!hasNpi) {
                out.add(new ContractFinding(r.getReference(), "ERROR", "Practitioner.identifier",
                        "No identifier with system " + FhirR4ExportService.NPI_SYSTEM + " (NPI); identifier.system is required by the Practitioner interface"));
            }
        } else if ("PractitionerRole".equals(type)) {
            if (!node.has("organization")) {
                out.add(new ContractFinding(r.getReference(), "ERROR", "PractitionerRole.organization",
                        "No department (Organization) reference; cost center did not resolve to a department"));
            }
        } else if ("Slot".equals(type)) {
            for (String field : new String[] {"start", "end"}) {
                String v = node.path(field).asText("");
                if (v.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?") ) {
                    out.add(new ContractFinding(r.getReference(), "ERROR", "Slot." + field,
                            "Instant '" + v + "' has no timezone offset; Scheduling interface requires America/New_York offset (e.g. -04:00)"));
                }
            }
        }
        return out;
    }

    public FhirContext getFhirContext() {
        return fhirContext;
    }
}
