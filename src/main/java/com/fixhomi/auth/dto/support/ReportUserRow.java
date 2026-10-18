package com.fixhomi.auth.dto.support;

import java.time.LocalDateTime;

/** One person in the daily report. Email / phone are masked. */
public record ReportUserRow(Long userId, String fullName, String role, String maskedEmail, String maskedPhone,
                            boolean active, boolean emailVerified, boolean phoneVerified,
                            LocalDateTime registeredAt, LocalDateTime lastLoginAt) {
}