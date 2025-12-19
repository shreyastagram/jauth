package com.fixhomi.auth.exception;

/**
 * Custom exception for invalid password errors.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
