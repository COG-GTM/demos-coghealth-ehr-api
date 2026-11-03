package com.medchart.ehr.service;

public class EncounterNotFoundException extends RuntimeException {

    public EncounterNotFoundException(Long encounterId) {
        super("Encounter " + encounterId + " not found");
    }
}
