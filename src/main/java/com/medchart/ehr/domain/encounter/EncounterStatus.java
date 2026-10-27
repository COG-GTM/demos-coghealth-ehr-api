package com.medchart.ehr.domain.encounter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum EncounterStatus {
    SCHEDULED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    public Set<EncounterStatus> allowedTransitions() {
        switch (this) {
            case SCHEDULED:
                return EnumSet.of(CHECKED_IN, CANCELLED, NO_SHOW);
            case CHECKED_IN:
                return EnumSet.of(IN_PROGRESS, CANCELLED, NO_SHOW);
            case IN_PROGRESS:
                return EnumSet.of(COMPLETED);
            default:
                return Collections.emptySet();
        }
    }

    public boolean canTransitionTo(EncounterStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
