package com.cloud.guardrails.exception;

public class ExternalValidationException extends RuntimeException {

    public ExternalValidationException(String message) {
        super(message);
    }

    public ExternalValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
