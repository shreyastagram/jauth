package com.fixhomi.auth.service.notification;

/**
 * Interface for SMS sending operations.
 * Implementations should handle actual SMS delivery via providers like Twilio.
 */
public interface SmsService {

    /**
     * Send OTP code for phone verification.
     *
     * @param phoneNumber recipient phone number (E.164 format preferred)
     * @param otp the OTP code
     * @return true if SMS was sent successfully
     */
    boolean sendOtp(String phoneNumber, String otp);

    /**
     * Send phone verification success notification.
     *
     * @param phoneNumber recipient phone number
     * @return true if SMS was sent successfully
     */
    boolean sendVerificationSuccess(String phoneNumber);
}
