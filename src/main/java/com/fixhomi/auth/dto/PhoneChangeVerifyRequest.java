package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to complete a phone change: verifies the OTP that was sent to the
 * NEW number and, only on success, atomically replaces the account's
 * phoneNumber and marks it verified.
 */
public class PhoneChangeVerifyRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+\\d][\\d\\s-]{7,17}$", message = "Invalid phone number format")
    private String phoneNumber;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{4,8}$", message = "Invalid OTP format")
    private String otp;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }
}
