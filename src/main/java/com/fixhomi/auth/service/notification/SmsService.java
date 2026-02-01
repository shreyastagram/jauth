package com.fixhomi.auth.service.notification;

/**
 * Interface for SMS and OTP operations using Twilio Verify API.
 * All OTP flows (phone verification, login, password reset, account deletion)
 * use Twilio Verify for secure, managed OTP handling.
 */
public interface SmsService {

    /**
     * Start phone verification using Twilio Verify API.
     * Twilio handles OTP generation, delivery, and expiry management.
     *
     * @param phoneNumber recipient phone number (E.164 format preferred)
     * @return true if verification was started successfully
     */
    boolean startVerification(String phoneNumber);

    /**
     * Check verification code using Twilio Verify API.
     * Twilio validates the OTP internally.
     *
     * @param phoneNumber the phone number being verified
     * @param code the OTP code entered by user
     * @return true if verification was successful
     */
    boolean checkVerification(String phoneNumber, String code);

    /**
     * Send phone verification success notification (optional).
     * Called after successful verification to notify the user.
     *
     * @param phoneNumber recipient phone number
     * @return true if SMS was sent successfully
     */
    default boolean sendVerificationSuccess(String phoneNumber) {
        // Optional - implementations may skip this
        return true;
    }
}

