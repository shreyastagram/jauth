package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for starting phone-number SIGNUP (account creation). The full name
 * is captured here so it can be carried through the OTP window and used at user
 * insert time. Email is NOT collected — phone-only users persist with email NULL.
 */
public class PhoneSignupSendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phoneNumber;

    @NotBlank(message = "Full name is required")
    @Size(min = 1, max = 100, message = "Full name must be 1-100 characters")
    private String fullName;

    public PhoneSignupSendOtpRequest() {
    }

    public PhoneSignupSendOtpRequest(String phoneNumber, String fullName) {
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
