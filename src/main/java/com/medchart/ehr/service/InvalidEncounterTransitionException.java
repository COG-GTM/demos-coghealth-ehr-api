package com.medchart.ehr.service;

import com.medchart.ehr.domain.encounter.EncounterStatus;

public class InvalidEncounterTransitionException extends IllegalStateException {

    public InvalidEncounterTransitionException(String message) {
        super(message);
    }

    public InvalidEncounterTransitionException(EncounterStatus from, EncounterStatus to) {
        super("Encounter cannot move from " + from + " to " + to);
    }
}
