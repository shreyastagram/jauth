package com.fixhomi.auth.dto.support;

import java.time.LocalDateTime;

import com.fixhomi.auth.entity.User;

/**
 * A customer / provider as a support agent sees it.
 * No password hash, and email / phone are masked (see DataMasking).
 */
public record SupportUserResponse(
        Long id, String fullName, String maskedEmail, String maskedPhone, String role,
        boolean active, boolean emailVerified, boolean phoneVerified,
        LocalDateTime createdAt, LocalDateTime lastLoginAt) {

    public static SupportUserResponse from(User u, String maskedEmail, String maskedPhone) {
        return new SupportUserResponse(u.getId(), u.getFullName(), maskedEmail, maskedPhone,
                u.getRole() == null ? null : u.getRole().name(),
                Boolean.TRUE.equals(u.getIsActive()),
                Boolean.TRUE.equals(u.getIsEmailVerified()),
                Boolean.TRUE.equals(u.getIsPhoneVerified()),
                u.getCreatedAt(), u.getLastLoginAt());
    }
}