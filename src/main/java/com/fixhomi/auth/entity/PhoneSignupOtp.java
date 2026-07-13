package com.fixhomi.auth.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * OTP for phone-number SIGNUP (account creation). Distinct from {@link PhoneOtp},
 * which is keyed on an existing user_id. A signup OTP exists before the user does,
 * so it is keyed purely on the phone number and carries the prospective user's
 * full name through the verification window. A {@link User} row is created only
 * after the OTP verifies — orphan accounts are not possible.
 */
@Entity
@Table(name = "phone_signup_otps", indexes = {
    @Index(name = "idx_phone_signup_otp_phone", columnList = "phone_number"),
    @Index(name = "idx_phone_signup_otp_expires", columnList = "expires_at")
})
@EntityListeners(AuditingEntityListener.class)
public class PhoneSignupOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(nullable = false, length = 10)
    private String otp;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(nullable = false)
    private Integer attempts = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PhoneSignupOtp() {
    }

    public PhoneSignupOtp(String phoneNumber, String otp, String fullName, LocalDateTime expiresAt) {
        this.phoneNumber = phoneNumber;
        this.otp = otp;
        this.fullName = fullName;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
