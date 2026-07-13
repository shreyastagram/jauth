package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for verifying a phone-signup OTP and creating the user.
 */
public class PhoneSignupVerifyRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phoneNumber;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^[0-9]{4,10}$", message = "OTP must be 4-10 digits")
    private String otp;

    public PhoneSignupVerifyRequest() {
    }

    public PhoneSignupVerifyRequest(String phoneNumber, String otp) {
        this.phoneNumber = phoneNumber;
        this.otp = otp;
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
