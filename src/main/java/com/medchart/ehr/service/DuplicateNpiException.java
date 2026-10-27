package com.medchart.ehr.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateNpiException extends RuntimeException {

    public DuplicateNpiException() {
        super("A provider with this NPI already exists");
    }
}
