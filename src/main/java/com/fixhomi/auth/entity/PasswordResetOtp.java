package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity for password reset OTPs.
 * Supports both phone-based and email-based OTP password reset.
 */
@Entity
@Table(name = "password_reset_otps", indexes = {
    @Index(name = "idx_pwd_reset_otp_phone", columnList = "phone_number"),
    @Index(name = "idx_pwd_reset_otp_email", columnList = "email"),
    @Index(name = "idx_pwd_reset_otp_expires", columnList = "expires_at"),
    @Index(name = "idx_pwd_reset_otp_user", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email", length = 255)
    private String email;

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

    // Constructors
    public PasswordResetOtp() {
    }

    /** Constructor for phone-based OTP */
    public PasswordResetOtp(Long userId, String phoneNumber, String otp, LocalDateTime expiresAt) {
        this.userId = userId;
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.expiresAt = expiresAt;
        this.used = false;
        this.attempts = 0;
    }

    /** Factory method for email-based OTP */
    public static PasswordResetOtp forEmail(Long userId, String email, String otp, LocalDateTime expiresAt) {
        PasswordResetOtp instance = new PasswordResetOtp();
        instance.userId = userId;
        instance.email = email;
        instance.otp = otp;
        instance.expiresAt = expiresAt;
        instance.used = false;
        instance.attempts = 0;
        return instance;
    }

    // Helper methods
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
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
