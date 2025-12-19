package com.fixhomi.auth.dto;

/**
 * Response DTO for verification operations.
 */
public class VerificationResponse {

    private boolean success;
    private String message;
    private String maskedTarget; // Masked email or phone

    // Constructors
    public VerificationResponse() {
    }

    public VerificationResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public VerificationResponse(boolean success, String message, String maskedTarget) {
        this.success = success;
        this.message = message;
        this.maskedTarget = maskedTarget;
    }

    // Static factory methods
    public static VerificationResponse success(String message) {
        return new VerificationResponse(true, message);
    }

    public static VerificationResponse success(String message, String maskedTarget) {
        return new VerificationResponse(true, message, maskedTarget);
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMaskedTarget() {
        return maskedTarget;
    }

    public void setMaskedTarget(String maskedTarget) {
        this.maskedTarget = maskedTarget;
    }
}
