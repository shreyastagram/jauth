package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of EmailService for development/testing.
 * Logs emails instead of sending them.
 * 
 * Active when: fixhomi.notification.email.provider=stub (or not set)
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.email.provider", havingValue = "stub", matchIfMissing = true)
public class StubEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public boolean sendEmailVerification(String toEmail, String fullName, String verificationToken, String verificationUrl) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Email Verification");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("TOKEN: {}", verificationToken);
        logger.info("VERIFICATION URL: {}", verificationUrl);
        logger.info("=========================================");
        return true;
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Password Reset");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("TOKEN: {}", resetToken);
        logger.info("RESET URL (Mobile Deep Link): {}", resetUrl);
        logger.info("=========================================");
        return true;
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String fullName, String resetToken, 
            String mobileResetUrl, String webResetUrl) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Password Reset (Mobile + Web)");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("TOKEN: {}", resetToken);
        logger.info("MOBILE DEEP LINK: {}", mobileResetUrl);
        logger.info("WEB FALLBACK URL: {}", webResetUrl);
        logger.info("=========================================");
        logger.info(">>> Use this URL on mobile: {}", mobileResetUrl);
        logger.info(">>> Or copy token manually: {}", resetToken);
        return true;
    }

    @Override
    public boolean sendWelcomeEmail(String toEmail, String fullName) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Welcome Email");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("=========================================");
        return true;
    }

    @Override
    public boolean sendPasswordChangedNotification(String toEmail, String fullName) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Password Changed Notification");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("=========================================");
        return true;
    }

    @Override
    public boolean sendLoginOtp(String toEmail, String fullName, String otp) {
        logger.info("========== STUB EMAIL SERVICE ==========");
        logger.info("EMAIL TYPE: Login OTP");
        logger.info("TO: {} ({})", toEmail, fullName);
        logger.info("OTP CODE: {}", otp);
        logger.info("MESSAGE: Your FixHomi login code is: {}. Valid for 5 minutes.", otp);
        logger.info("=========================================");
        return true;
    }
}
