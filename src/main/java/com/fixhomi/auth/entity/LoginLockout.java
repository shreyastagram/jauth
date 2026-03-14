package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity for persistent brute force / login lockout tracking.
 * Replaces in-memory ConcurrentHashMap to survive server restarts.
 *
 * Tracks failed login attempts per identifier (email or phone) and optionally
 * per userId for unified cross-method lockout.
 */
@Entity
@Table(name = "login_lockouts", indexes = {
    @Index(name = "idx_lockout_identifier", columnList = "identifier"),
    @Index(name = "idx_lockout_user_id", columnList = "user_id"),
    @Index(name = "idx_lockout_locked_until", columnList = "locked_until")
})
@EntityListeners(AuditingEntityListener.class)
public class LoginLockout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The login identifier used (email or phone number) */
    @Column(nullable = false, length = 255)
    private String identifier;

    /** The user ID if known (for cross-method lockout) */
    @Column(name = "user_id")
    private Long userId;

    /** Number of consecutive failed attempts */
    @Column(name = "failed_attempts", nullable = false)
    private Integer failedAttempts = 0;

    /** When the lockout expires (null if not locked) */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** When the last failed attempt occurred */
    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LoginLockout() {}

    public LoginLockout(String identifier) {
        this.identifier = identifier;
        this.failedAttempts = 0;
    }

    public boolean isLocked() {
        return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
    }

    public void incrementAttempts(int maxAttempts, long lockoutDurationMinutes) {
        this.failedAttempts++;
        this.lastAttemptAt = LocalDateTime.now();
        if (this.failedAttempts >= maxAttempts) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockoutDurationMinutes);
        }
    }

    public void resetAttempts() {
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Integer getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(Integer failedAttempts) { this.failedAttempts = failedAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public LocalDateTime getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(LocalDateTime lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
