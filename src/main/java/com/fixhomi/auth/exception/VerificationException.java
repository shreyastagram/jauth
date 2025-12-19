package com.fixhomi.auth.exception;

/**
 * Exception thrown when verification operations fail.
 */
public class VerificationException extends RuntimeException {

    public VerificationException(String message) {
        super(message);
    }

    public VerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
