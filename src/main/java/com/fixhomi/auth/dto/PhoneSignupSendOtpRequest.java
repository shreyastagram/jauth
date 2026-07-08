package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for starting phone-number SIGNUP (account creation). The full name
 * is OPTIONAL — newer app versions collect it post-signup, so a blank/absent name
 * is stored as "" and the account keeps an empty name until the user fills it
 * (required before booking a request). The field is kept for backward compatibility
 * with older app versions that still send it. Email is NOT collected — phone-only
 * users persist with email NULL.
 */
public class PhoneSignupSendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
    private String phoneNumber;

    @Size(max = 100, message = "Full name must be at most 100 characters")
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
