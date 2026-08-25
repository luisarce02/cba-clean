package com.cbclean.incident.domain.model;

public class InvalidIncidentException extends RuntimeException {

    public InvalidIncidentException(String message) {
        super(message);
    }
}
