package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Twilio implementation of SmsService.
 * 
 * Active when: fixhomi.notification.sms.provider=twilio
 * 
 * TODO: Implement actual Twilio API integration.
 * Required dependency: com.twilio.sdk:twilio
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "twilio")
public class TwilioSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsService.class);

    @Value("${fixhomi.notification.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${fixhomi.notification.sms.twilio.auth-token:}")
    private String authToken;

    @Value("${fixhomi.notification.sms.twilio.phone-number:}")
    private String fromPhoneNumber;

    @Override
    public boolean sendOtp(String phoneNumber, String otp) {
        logger.info("Sending OTP via Twilio to: {}", phoneNumber);
        
        // TODO: Implement Twilio API call
        // Twilio.init(accountSid, authToken);
        // Message message = Message.creator(
        //     new PhoneNumber(phoneNumber),
        //     new PhoneNumber(fromPhoneNumber),
        //     "Your FixHomi verification code is: " + otp + ". Valid for 5 minutes."
        // ).create();
        
        logger.warn("Twilio SMS service not fully implemented yet. OTP: {}", otp);
        return false;
    }

    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        logger.info("Sending verification success SMS via Twilio to: {}", phoneNumber);
        
        // TODO: Implement Twilio API call
        logger.warn("Twilio SMS service not fully implemented yet.");
        return false;
    }
}
