package com.fixhomi.auth.exception;

/**
 * Custom exception for duplicate resource errors.
 * Carries the conflicting field name (e.g., "email", "phoneNumber")
 * so the error handler can return specific error codes to clients.
 */
public class DuplicateResourceException extends RuntimeException {

    private String field;
    private String resource;

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resource, String field, Object value) {
        super(String.format("%s already exists with %s: '%s'", resource, field, value));
        this.resource = resource;
        this.field = field;
    }

    /**
     * Get the conflicting field name (e.g., "email", "phoneNumber")
     */
    public String getField() {
        return field;
    }

    /**
     * Get the resource type (e.g., "User")
     */
    public String getResource() {
        return resource;
    }
}
