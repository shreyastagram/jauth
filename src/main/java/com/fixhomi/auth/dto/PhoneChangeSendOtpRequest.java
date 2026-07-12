package com.fixhomi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request to start a verify-then-replace phone change: an OTP is sent to the
 * NEW number while the account's current (old) number stays untouched. The
 * account's phoneNumber only changes when the OTP is verified
 * (see {@link PhoneChangeVerifyRequest}).
 *
 * Also used to ADD a number to phone-less accounts (Google/Apple signups) and
 * to re-verify a stored-but-unverified number — same flow in all three cases.
 */
public class PhoneChangeSendOtpRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+\\d][\\d\\s-]{7,17}$", message = "Invalid phone number format")
    private String phoneNumber;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
