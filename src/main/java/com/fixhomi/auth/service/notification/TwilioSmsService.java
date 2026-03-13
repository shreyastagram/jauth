package com.fixhomi.auth.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Twilio Programmable SMS implementation of SmsService.
 * Sends OTP via Twilio SMS API. OTP generation and verification is local.
 *
 * Active when: fixhomi.notification.sms.provider=twilio
 *
 * Required environment variables:
 * - TWILIO_ACCOUNT_SID
 * - TWILIO_AUTH_TOKEN
 * - TWILIO_PHONE_NUMBER (sender phone number)
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "twilio")
public class TwilioSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsService.class);

    private static final String TWILIO_SMS_URL = "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    @Value("${fixhomi.notification.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${fixhomi.notification.sms.twilio.auth-token:}")
    private String authToken;

    @Value("${fixhomi.notification.sms.twilio.phone-number:}")
    private String fromNumber;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TwilioSmsService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void validateConfiguration() {
        if (accountSid == null || accountSid.isBlank()) {
            logger.error("TWILIO_ACCOUNT_SID is not configured!");
        }
        if (authToken == null || authToken.isBlank()) {
            logger.error("TWILIO_AUTH_TOKEN is not configured!");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            logger.error("TWILIO_PHONE_NUMBER is not configured!");
        }
        if (accountSid != null && !accountSid.isBlank()) {
            logger.info("Twilio SMS Service configured with SID: {}...",
                accountSid.substring(0, Math.min(10, accountSid.length())));
        }
    }

    @Override
    public boolean sendOtp(String phoneNumber, String otp) {
        String normalizedPhone = normalizeToE164(phoneNumber);
        logger.info("Sending OTP via Twilio SMS to: {}", maskPhoneNumber(normalizedPhone));

        try {
            validateConfig();

            String url = String.format(TWILIO_SMS_URL, accountSid);

            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String message = "Your FixHomi verification code is: " + otp + ". Do not share this with anyone.";
            String body = String.format("To=%s&From=%s&Body=%s",
                    encodeUrlParam(normalizedPhone),
                    encodeUrlParam(fromNumber),
                    encodeUrlParam(message));

            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("OTP SMS sent successfully via Twilio to: {}", maskPhoneNumber(normalizedPhone));
                return true;
            } else {
                logger.error("Twilio SMS failed. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            handleTwilioError(e, normalizedPhone);
            return false;
        } catch (Exception e) {
            logger.error("Twilio SMS failed for {}: {}", maskPhoneNumber(normalizedPhone), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        logger.debug("Phone verified: {} (success SMS skipped)", maskPhoneNumber(phoneNumber));
        return true;
    }

    private void validateConfig() {
        if (accountSid == null || accountSid.isBlank()) {
            throw new IllegalStateException("Twilio Account SID is not configured");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("Twilio Auth Token is not configured");
        }
        if (fromNumber == null || fromNumber.isBlank()) {
            throw new IllegalStateException("Twilio Phone Number is not configured");
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = accountSid + ":" + authToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    private String encodeUrlParam(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    private String normalizeToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) return phoneNumber;
        String cleaned = phoneNumber.trim();
        boolean hasPlus = cleaned.startsWith("+");
        cleaned = cleaned.replaceAll("[^0-9]", "");
        if (hasPlus) return "+" + cleaned;
        if (cleaned.length() == 10) return "+91" + cleaned;
        if (cleaned.length() == 12 && cleaned.startsWith("91")) return "+" + cleaned;
        return "+" + cleaned;
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) return "****";
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private void handleTwilioError(HttpClientErrorException e, String phone) {
        try {
            JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
            String errorMessage = errorJson.path("message").asText();
            logger.error("Twilio SMS error ({}) for {}: {}", e.getStatusCode(), maskPhoneNumber(phone), errorMessage);
        } catch (Exception parseError) {
            logger.error("Twilio SMS error ({}) for {}", e.getStatusCode(), maskPhoneNumber(phone));
        }
    }
}
