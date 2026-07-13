package com.fixhomi.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to set (or change) the email on an account after signup.
 * Used by phone-only USERS who add an email later, and by any USER who
 * changes their email — in both cases the email is stored UNVERIFIED and a
 * fresh verification link is sent. Email value lives in JAuth (source of truth)
 * and is mirrored into the NoeFix Mongo profile by the NoeFix orchestration.
 */
public class SetEmailRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
