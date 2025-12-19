package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Stub implementation of SmsService for development/testing.
 * Logs SMS messages instead of sending them.
 * 
 * Active when: fixhomi.notification.sms.provider=stub (or not set)
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "stub", matchIfMissing = true)
public class StubSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(StubSmsService.class);

    @Override
    public boolean sendOtp(String phoneNumber, String otp) {
        logger.info("========== STUB SMS SERVICE ==========");
        logger.info("SMS TYPE: OTP Verification");
        logger.info("TO: {}", phoneNumber);
        logger.info("OTP CODE: {}", otp);
        logger.info("MESSAGE: Your FixHomi verification code is: {}. Valid for 5 minutes.", otp);
        logger.info("=======================================");
        return true;
    }

    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        logger.info("========== STUB SMS SERVICE ==========");
        logger.info("SMS TYPE: Verification Success");
        logger.info("TO: {}", phoneNumber);
        logger.info("MESSAGE: Your phone number has been successfully verified on FixHomi.");
        logger.info("=======================================");
        return true;
    }
}
