package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity for delete account OTP verification.
 * Replaces in-memory ConcurrentHashMap to survive server restarts.
 */
@Entity
@Table(name = "delete_account_otps", indexes = {
    @Index(name = "idx_del_otp_phone", columnList = "phone_number"),
    @Index(name = "idx_del_otp_user", columnList = "user_id"),
    @Index(name = "idx_del_otp_expires", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
public class DeleteAccountOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 10)
    private String otp;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(nullable = false)
    private Integer attempts = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DeleteAccountOtp() {}

    public DeleteAccountOtp(Long userId, String phoneNumber, String otp, LocalDateTime expiresAt) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.expiresAt = expiresAt;
        this.used = false;
        this.attempts = 0;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markAsUsed() {
        this.used = true;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
