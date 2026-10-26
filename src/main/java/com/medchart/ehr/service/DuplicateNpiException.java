package com.medchart.ehr.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT, reason = "A provider with this NPI already exists")
public class DuplicateNpiException extends RuntimeException {

    public DuplicateNpiException() {
        super("A provider with this NPI already exists");
    }
}
