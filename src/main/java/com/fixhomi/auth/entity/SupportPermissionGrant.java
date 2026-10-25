package com.fixhomi.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

/** One row = "this support user has this permission". */
@Entity
@Table(name = "support_permission_grants",
       uniqueConstraints = @UniqueConstraint(name = "uk_support_grant_user_perm",
                                             columnNames = {"user_id", "permission"}),
       indexes = @Index(name = "idx_support_grant_user", columnList = "user_id"))
@EntityListeners(AuditingEntityListener.class)
public class SupportPermissionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The SUPPORT user who holds the permission (plain number, no foreign key - see guide). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Stored as text (the name of a SupportPermission value) - see guide for why. */
    @Column(name = "permission", nullable = false, length = 50)
    private String permission;

    /** The admin who granted it. */
    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @CreatedDate
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    public SupportPermissionGrant() {
    }

    public SupportPermissionGrant(Long userId, String permission, Long grantedBy) {
        this.userId = userId;
        this.permission = permission;
        this.grantedBy = grantedBy;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPermission() { return permission; }
    public Long getGrantedBy() { return grantedBy; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}
