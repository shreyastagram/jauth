package com.fixhomi.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an invalid or unauthorized role is requested.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class InvalidRoleException extends RuntimeException {

    public InvalidRoleException(String message) {
        super(message);
    }

    public InvalidRoleException(String role, String reason) {
        super(String.format("Role '%s' is not allowed: %s", role, reason));
    }
}
