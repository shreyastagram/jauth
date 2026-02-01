package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub implementation of SmsService for development/testing.
 * Logs OTP messages and stores them in memory for verification.
 * 
 * Active when: fixhomi.notification.sms.provider=stub (or not set)
 * 
 * In development mode, OTPs are logged and can be verified.
 * Default OTP for testing: 123456
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "stub", matchIfMissing = true)
public class StubSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(StubSmsService.class);
    
    // Store pending verifications (phone -> otp)
    private final Map<String, String> pendingVerifications = new ConcurrentHashMap<>();
    
    // Default test OTP for development
    private static final String DEV_OTP = "123456";

    @Override
    public boolean startVerification(String phoneNumber) {
        logger.info("╔════════════════════════════════════════╗");
        logger.info("║     STUB SMS SERVICE - DEV MODE        ║");
        logger.info("╠════════════════════════════════════════╣");
        logger.info("║ TYPE: OTP Verification Started         ║");
        logger.info("║ TO: {}", padRight(phoneNumber, 30) + "║");
        logger.info("║ OTP CODE: {}", padRight(DEV_OTP, 26) + "║");
        logger.info("║                                        ║");
        logger.info("║ ⚠️  Use code {} to verify         ║", DEV_OTP);
        logger.info("╚════════════════════════════════════════╝");
        
        pendingVerifications.put(normalizePhone(phoneNumber), DEV_OTP);
        return true;
    }

    @Override
    public boolean checkVerification(String phoneNumber, String code) {
        String normalizedPhone = normalizePhone(phoneNumber);
        String storedOtp = pendingVerifications.get(normalizedPhone);
        
        logger.info("╔════════════════════════════════════════╗");
        logger.info("║     STUB SMS SERVICE - DEV MODE        ║");
        logger.info("╠════════════════════════════════════════╣");
        logger.info("║ TYPE: OTP Verification Check           ║");
        logger.info("║ PHONE: {}", padRight(phoneNumber, 28) + "║");
        logger.info("║ CODE ENTERED: {}", padRight(code, 22) + "║");
        logger.info("║ EXPECTED: {}", padRight(storedOtp != null ? storedOtp : DEV_OTP, 26) + "║");
        
        // Accept either the stored OTP or the default DEV_OTP
        boolean isValid = DEV_OTP.equals(code) || (storedOtp != null && storedOtp.equals(code));
        
        if (isValid) {
            logger.info("║ RESULT: ✅ VERIFIED                    ║");
            pendingVerifications.remove(normalizedPhone);
        } else {
            logger.info("║ RESULT: ❌ INVALID                     ║");
        }
        logger.info("╚════════════════════════════════════════╝");
        
        return isValid;
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
    
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9+]", "");
    }
    
    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }
}
