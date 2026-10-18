package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * One row means: "support agent X was given permission P by admin A".
 * Table: support_permission_grants
 */
@Entity
@Table(name = "support_permission_grants",
        uniqueConstraints = @UniqueConstraint(name = "uk_support_grant_user_permission",
                columnNames = {"user_id", "permission"}))
@EntityListeners(AuditingEntityListener.class)
public class SupportPermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The support agent who received the permission. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SupportPermission permission;

    /** The admin who gave the permission. */
    @Column(name = "granted_by_user_id", nullable = false)
    private Long grantedByUserId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    public SupportPermissionGrant() {
    }

    public SupportPermissionGrant(Long userId, SupportPermission permission, Long grantedByUserId) {
        this.userId = userId;
        this.permission = permission;
        this.grantedByUserId = grantedByUserId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public SupportPermission getPermission() { return permission; }
    public Long getGrantedByUserId() { return grantedByUserId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}