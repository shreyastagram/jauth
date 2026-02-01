package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for account deletion with OTP verification.
 * 
 * For production security, account deletion requires:
 * 1. Phone OTP verification
 * 2. Optional reason for feedback
 */
public class DeleteAccountRequest {

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be 6 digits")
    private String otp;

    private String reason;

    public DeleteAccountRequest() {}

    public DeleteAccountRequest(String otp, String reason) {
        this.otp = otp;
        this.reason = reason;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
