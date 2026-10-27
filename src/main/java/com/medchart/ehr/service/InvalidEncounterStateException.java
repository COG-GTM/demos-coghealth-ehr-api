package com.medchart.ehr.service;

public class InvalidEncounterStateException extends RuntimeException {

    public InvalidEncounterStateException(String message) {
        super(message);
    }
}
