package com.fixhomi.auth.service.notification;

/**
 * Interface for email sending operations.
 * Implementations should handle actual email delivery via providers like Brevo.
 */
public interface EmailService {

    /**
     * Send email verification link to user.
     *
     * @param toEmail recipient email address
     * @param fullName recipient's full name
     * @param verificationToken the verification token
     * @param verificationUrl the full verification URL
     * @return true if email was sent successfully
     */
    boolean sendEmailVerification(String toEmail, String fullName, String verificationToken, String verificationUrl);

    /**
     * Send password reset link to user.
     *
     * @param toEmail recipient email address
     * @param fullName recipient's full name
     * @param resetToken the reset token
     * @param resetUrl the full reset URL
     * @return true if email was sent successfully
     */
    boolean sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl);

    /**
     * Send welcome email after successful registration.
     *
     * @param toEmail recipient email address
     * @param fullName recipient's full name
     * @return true if email was sent successfully
     */
    boolean sendWelcomeEmail(String toEmail, String fullName);

    /**
     * Send password changed notification.
     *
     * @param toEmail recipient email address
     * @param fullName recipient's full name
     * @return true if email was sent successfully
     */
    boolean sendPasswordChangedNotification(String toEmail, String fullName);

    /**
     * Send OTP for passwordless login via email.
     *
     * @param toEmail recipient email address
     * @param fullName recipient's full name
     * @param otp the one-time password
     * @return true if email was sent successfully
     */
    boolean sendLoginOtp(String toEmail, String fullName, String otp);
}
