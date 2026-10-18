package com.fixhomi.auth.dto.support;

import com.fixhomi.auth.entity.SupportAuditLog;

import java.time.LocalDateTime;

public record AuditLogResponse(Long id, String action, String targetType, Long targetId,
                               String details, LocalDateTime createdAt) {

    public static AuditLogResponse from(SupportAuditLog l) {
        return new AuditLogResponse(l.getId(), l.getAction().name(), l.getTargetType(), l.getTargetId(),
                l.getDetails(), l.getCreatedAt());
    }
}