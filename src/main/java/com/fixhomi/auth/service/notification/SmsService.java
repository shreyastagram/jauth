package com.fixhomi.auth.service.notification;

/**
 * Interface for SMS OTP operations.
 * OTP generation and verification is handled locally by the caller.
 * Implementations are responsible only for delivering the SMS.
 */
public interface SmsService {

    /**
     * Send an OTP via SMS using the default (login) template.
     *
     * @param phoneNumber recipient phone number
     * @param otp the OTP code to send
     * @return true if SMS was sent successfully
     */
    boolean sendOtp(String phoneNumber, String otp);

    /**
     * Send an OTP via SMS using a specific template ID.
     * Falls back to the default template if templateId is null or blank.
     *
     * @param phoneNumber recipient phone number
     * @param otp the OTP code to send
     * @param templateId MSG91 template ID to use
     * @return true if SMS was sent successfully
     */
    default boolean sendOtp(String phoneNumber, String otp, String templateId) {
        return sendOtp(phoneNumber, otp);
    }

    /**
     * Send phone verification success notification (optional).
     *
     * @param phoneNumber recipient phone number
     * @return true if SMS was sent successfully
     */
    default boolean sendVerificationSuccess(String phoneNumber) {
        return true;
    }
}
