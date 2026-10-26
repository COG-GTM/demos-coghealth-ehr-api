package com.medchart.ehr.legacy;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidExportDateException extends IllegalArgumentException {

    public InvalidExportDateException(String message) {
        super(message);
    }
}
