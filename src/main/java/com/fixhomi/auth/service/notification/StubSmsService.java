package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of SmsService for development/testing.
 * Logs OTP messages to console instead of sending real SMS.
 *
 * Active when: fixhomi.notification.sms.provider=stub (or not set)
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "stub", matchIfMissing = true)
public class StubSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(StubSmsService.class);

    @Override
    public boolean sendOtp(String phoneNumber, String otp) {
        logger.info("╔════════════════════════════════════════╗");
        logger.info("║     STUB SMS SERVICE - DEV MODE        ║");
        logger.info("╠════════════════════════════════════════╣");
        logger.info("║ TO: {}", padRight(phoneNumber, 30) + "║");
        logger.info("║ OTP CODE: {}", padRight(otp, 26) + "║");
        logger.info("║                                        ║");
        logger.info("║ Use code {} to verify              ║", padRight(otp, 6));
        logger.info("╚════════════════════════════════════════╝");
        return true;
    }

    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        logger.info("╔════════════════════════════════════════╗");
        logger.info("║     STUB SMS SERVICE - DEV MODE        ║");
        logger.info("╠════════════════════════════════════════╣");
        logger.info("║ TYPE: Verification Success             ║");
        logger.info("║ TO: {}", padRight(phoneNumber, 30) + "║");
        logger.info("║ MESSAGE: Phone verified successfully!  ║");
        logger.info("╚════════════════════════════════════════╝");
        return true;
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }
}
