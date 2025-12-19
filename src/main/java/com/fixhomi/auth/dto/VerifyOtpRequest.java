package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for OTP verification.
 */
public class VerifyOtpRequest {

    @NotBlank(message = "OTP is required")
    @Size(min = 4, max = 10, message = "OTP must be between 4 and 10 digits")
    @Pattern(regexp = "^[0-9]+$", message = "OTP must contain only digits")
    private String otp;

    // Constructors
    public VerifyOtpRequest() {
    }

    public VerifyOtpRequest(String otp) {
        this.otp = otp;
    }

    // Getters and Setters
    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
