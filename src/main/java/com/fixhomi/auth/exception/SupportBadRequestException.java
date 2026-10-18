package com.fixhomi.auth.exception;

/** Thrown when a support request has wrong input (for example a date outside the allowed range). */
public class SupportBadRequestException extends RuntimeException {
    public SupportBadRequestException(String message) {
        super(message);
    }
}