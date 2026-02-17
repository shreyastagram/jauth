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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready Twilio Verify implementation of SmsService.
 * 
 * Uses Twilio Verify API exclusively - no "from" phone number required.
 * Twilio handles OTP generation, delivery, expiry (10 minutes), and validation.
 * 
 * Active when: fixhomi.notification.sms.provider=twilio
 * 
 * Required environment variables:
 * - TWILIO_ACCOUNT_SID: Your Twilio Account SID
 * - TWILIO_AUTH_TOKEN: Your Twilio Auth Token  
 * - TWILIO_VERIFY_SERVICE_SID: Your Twilio Verify Service SID
 * 
 * Features:
 * - Phone verification (login, signup)
 * - Password reset OTP
 * - Account deletion confirmation
 * - Automatic rate limiting by Twilio
 * - OTP expiry managed by Twilio (10 min default)
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.sms.provider", havingValue = "twilio")
public class TwilioSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsService.class);
    
    private static final String TWILIO_VERIFY_BASE_URL = "https://verify.twilio.com/v2/Services";
    
    // Fallback OTP storage for unverified numbers in trial mode
    private final Map<String, String> fallbackOtpStorage = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${fixhomi.notification.sms.twilio.account-sid:}")
    private String accountSid;

    @Value("${fixhomi.notification.sms.twilio.auth-token:}")
    private String authToken;

    @Value("${fixhomi.notification.sms.twilio.verify-service-sid:}")
    private String verifyServiceSid;
    
    // Trial mode: logs instructions for testing with unverified numbers
    @Value("${fixhomi.notification.sms.twilio.trial-mode:true}")
    private boolean trialMode;
    
    // Verified phone number for trial mode (only this number will receive OTPs)
    @Value("${fixhomi.notification.sms.twilio.verified-phone:}")
    private String verifiedPhone;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TwilioSmsService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void validateConfiguration() {
        if (accountSid == null || accountSid.isBlank()) {
            logger.error("❌ CRITICAL: TWILIO_ACCOUNT_SID is not configured!");
        }
        if (authToken == null || authToken.isBlank()) {
            logger.error("❌ CRITICAL: TWILIO_AUTH_TOKEN is not configured!");
        }
        if (verifyServiceSid == null || verifyServiceSid.isBlank()) {
            logger.error("❌ CRITICAL: TWILIO_VERIFY_SERVICE_SID is not configured!");
        } else {
            logger.info("✅ Twilio Verify Service configured with SID: {}...", 
                verifyServiceSid.substring(0, Math.min(10, verifyServiceSid.length())));
        }
    }

    /**
     * Start phone verification using Twilio Verify API.
     * Twilio generates and sends the OTP automatically.
     *
     * @param phoneNumber recipient phone number (any format - will be normalized to E.164)
     * @return true if verification was started successfully
     */
    @Override
    public boolean startVerification(String phoneNumber) {
        // Normalize phone number to E.164 format for Twilio
        String normalizedPhone = normalizeToE164(phoneNumber);
        logger.info("📱 Starting Twilio Verify for: {} (normalized from: {})", 
            maskPhoneNumber(normalizedPhone), maskPhoneNumber(phoneNumber));
        
        // TRIAL MODE WORKAROUND: Redirect OTP to the verified phone number
        // Twilio trial accounts can only send to verified numbers.
        // We send the OTP to the verified phone but track it against the original number.
        String deliveryPhone = normalizedPhone;
        if (trialMode && verifiedPhone != null && !verifiedPhone.isBlank()) {
            deliveryPhone = normalizeToE164(verifiedPhone);
            logger.info("╔════════════════════════════════════════════════════════════╗");
            logger.info("║  ⚠️  TRIAL MODE: REDIRECTING OTP TO VERIFIED PHONE         ║");
            logger.info("╠════════════════════════════════════════════════════════════╣");
            logger.info("║ Requested by: {}                            ║", padRight(maskPhoneNumber(normalizedPhone), 18));
            logger.info("║ Sending to:   {}                            ║", padRight(maskPhoneNumber(deliveryPhone), 18));
            logger.info("║ Check the verified phone for the OTP code!                ║");
            logger.info("╚════════════════════════════════════════════════════════════╝");
        }
        
        try {
            validateConfig();
            
            String url = String.format("%s/%s/Verifications", TWILIO_VERIFY_BASE_URL, verifyServiceSid);
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Send OTP to deliveryPhone (verified phone in trial mode, actual phone in production)
            String body = String.format("To=%s&Channel=sms", encodeUrlParam(deliveryPhone));
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String status = responseJson.path("status").asText();
                
                if ("pending".equals(status)) {
                    logger.info("✅ OTP sent successfully to: {}", maskPhoneNumber(normalizedPhone));
                    
                    // Trial mode logging - helpful for development with unverified numbers
                    if (trialMode) {
                        logger.info("╔════════════════════════════════════════════════════════════╗");
                        logger.info("║        ⚠️  TWILIO TRIAL MODE - OTP SENT                    ║");
                        logger.info("╠════════════════════════════════════════════════════════════╣");
                        logger.info("║ Target: {}                       ║", padRight(normalizedPhone, 22));
                        logger.info("║                                                            ║");
                        if (verifiedPhone != null && !verifiedPhone.isBlank()) {
                            logger.info("║ ✅ Verified phone: {}              ║", padRight(verifiedPhone, 18));
                            logger.info("║                                                            ║");
                            if (normalizedPhone.endsWith(verifiedPhone.replaceAll("[^0-9]", "").substring(Math.max(0, verifiedPhone.replaceAll("[^0-9]", "").length() - 10)))) {
                                logger.info("║ 📱 OTP will be delivered to this phone!                   ║");
                            } else {
                                logger.info("║ ⚠️  TRIAL: OTP only sent to verified phone!               ║");
                                logger.info("║ Check the verified phone for OTP, then use it here.       ║");
                            }
                        } else {
                            logger.info("║ ⚠️  No verified phone configured                          ║");
                            logger.info("║ Set TWILIO_VERIFIED_PHONE in environment                  ║");
                        }
                        logger.info("║                                                            ║");
                        logger.info("║ TIP: Check Twilio Console for verification logs:           ║");
                        logger.info("║ https://console.twilio.com/us1/monitor/logs/sms            ║");
                        logger.info("╚════════════════════════════════════════════════════════════╝");
                    }
                    
                    return true;
                } else {
                    logger.warn("⚠️ Unexpected Twilio Verify status: {}", status);
                    return false;
                }
            } else {
                logger.error("❌ Twilio Verify start failed. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            // Check if it's error 21608 (unverified number in trial mode)
            if (trialMode && isUnverifiedNumberError(e)) {
                return generateFallbackOtp(normalizedPhone);
            }
            handleTwilioError(e, "startVerification", normalizedPhone);
            return false;
        } catch (Exception e) {
            logger.error("❌ Failed to start Twilio Verify: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verify OTP code using Twilio Verify API.
     * Twilio validates the code against what it sent.
     * Falls back to local storage for unverified numbers in trial mode.
     *
     * @param phoneNumber the phone number being verified (any format - will be normalized to E.164)
     * @param code the OTP code entered by user (6 digits)
     * @return true if verification was successful
     */
    @Override
    public boolean checkVerification(String phoneNumber, String code) {
        // Normalize phone number to E.164 format for Twilio
        String normalizedPhone = normalizeToE164(phoneNumber);
        logger.info("📱 Verifying OTP for: {}", maskPhoneNumber(normalizedPhone));
        
        // First, check if we have a fallback OTP stored (for unverified numbers in trial mode)
        if (trialMode && fallbackOtpStorage.containsKey(normalizedPhone)) {
            return checkFallbackOtp(normalizedPhone, code);
        }
        
        // TRIAL MODE WORKAROUND: Verify against the verified phone 
        // since that's where the OTP was actually sent
        String verifyPhone = normalizedPhone;
        if (trialMode && verifiedPhone != null && !verifiedPhone.isBlank()) {
            verifyPhone = normalizeToE164(verifiedPhone);
            logger.info("⚠️ TRIAL MODE: Verifying OTP against verified phone {} (original: {})", 
                maskPhoneNumber(verifyPhone), maskPhoneNumber(normalizedPhone));
        }
        
        try {
            validateConfig();
            
            // Validate OTP format (should be 6 digits)
            if (code == null || !code.matches("\\d{6}")) {
                logger.warn("❌ Invalid OTP format. Expected 6 digits, got: {}", 
                    code != null ? code.length() + " chars" : "null");
                return false;
            }
            
            String url = String.format("%s/%s/VerificationCheck", TWILIO_VERIFY_BASE_URL, verifyServiceSid);
            
            HttpHeaders headers = createAuthHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            // Use verifyPhone (verified phone in trial mode) for verification check
            String body = String.format("To=%s&Code=%s", encodeUrlParam(verifyPhone), encodeUrlParam(code));
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String status = responseJson.path("status").asText();
                
                if ("approved".equals(status)) {
                    logger.info("✅ OTP verified successfully for: {}", maskPhoneNumber(normalizedPhone));
                    return true;
                } else if ("pending".equals(status)) {
                    logger.warn("❌ OTP verification failed - incorrect code for: {}", maskPhoneNumber(normalizedPhone));
                    return false;
                } else {
                    logger.warn("❌ OTP verification status: {} for: {}", status, maskPhoneNumber(normalizedPhone));
                    return false;
                }
            } else {
                logger.error("❌ Twilio Verify check failed. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            handleTwilioError(e, "checkVerification", normalizedPhone);
            return false;
        } catch (Exception e) {
            logger.error("❌ Failed to check Twilio Verify: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send verification success notification (optional).
     * Most apps skip this as the UI handles success feedback.
     */
    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        // Skip sending success SMS to save costs - UI handles success feedback
        logger.debug("📱 Phone verified: {} (success SMS skipped)", maskPhoneNumber(phoneNumber));
        return true;
    }

    /**
     * Validate Twilio configuration before making API calls.
     */
    private void validateConfig() {
        if (accountSid == null || accountSid.isBlank()) {
            throw new IllegalStateException("Twilio Account SID is not configured");
        }
        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException("Twilio Auth Token is not configured");
        }
        if (verifyServiceSid == null || verifyServiceSid.isBlank()) {
            throw new IllegalStateException("Twilio Verify Service SID is not configured");
        }
    }

    /**
     * Create HTTP headers with Basic Auth for Twilio API.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String auth = accountSid + ":" + authToken;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedAuth);
        return headers;
    }

    /**
     * URL encode a parameter value.
     */
    private String encodeUrlParam(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * Mask phone number for logging (show last 4 digits only).
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    /**
     * Normalize phone number to E.164 format for Twilio.
     * E.164 format: +[country code][number] e.g., +919876543210
     * 
     * Handles:
     * - Numbers with +91 prefix (already E.164)
     * - Numbers with 91 prefix (missing +)
     * - 10-digit Indian mobile numbers (adds +91)
     * - Numbers with 0 prefix (removes 0, adds +91)
     * 
     * @param phoneNumber input phone number in any format
     * @return phone number in E.164 format
     */
    private String normalizeToE164(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return phoneNumber;
        }
        
        // Remove all non-digit characters except leading +
        String cleaned = phoneNumber.trim();
        boolean hasPlus = cleaned.startsWith("+");
        cleaned = cleaned.replaceAll("[^0-9]", "");
        
        // Already has +91 prefix (12 digits after removing +)
        if (hasPlus && cleaned.length() == 12 && cleaned.startsWith("91")) {
            return "+" + cleaned;
        }
        
        // Already has + and country code
        if (hasPlus && cleaned.length() >= 10) {
            return "+" + cleaned;
        }
        
        // Has 91 prefix but no + (12 digits starting with 91)
        if (cleaned.length() == 12 && cleaned.startsWith("91")) {
            return "+" + cleaned;
        }
        
        // Has 0 prefix (common in India for landlines/STD) - 11 digits starting with 0
        if (cleaned.length() == 11 && cleaned.startsWith("0")) {
            return "+91" + cleaned.substring(1);
        }
        
        // Standard 10-digit Indian mobile number
        if (cleaned.length() == 10) {
            // Indian mobile numbers start with 6, 7, 8, or 9
            char firstDigit = cleaned.charAt(0);
            if (firstDigit >= '6' && firstDigit <= '9') {
                return "+91" + cleaned;
            }
            // If not Indian, still try adding +91 (most common for this app)
            return "+91" + cleaned;
        }
        
        // For other lengths, try to preserve as-is with + prefix
        if (cleaned.length() > 10) {
            return "+" + cleaned;
        }
        
        // Fallback: assume Indian number
        logger.warn("⚠️ Unusual phone number format: {} - attempting +91 prefix", maskPhoneNumber(phoneNumber));
        return "+91" + cleaned;
    }

    /**
     * Handle Twilio API errors with proper logging.
     */
    private void handleTwilioError(HttpClientErrorException e, String operation, String phoneNumber) {
        try {
            JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
            int errorCode = errorJson.path("code").asInt();
            String errorMessage = errorJson.path("message").asText();
            
            // Handle specific Twilio error codes
            switch (errorCode) {
                case 20003:
                    logger.error("❌ [{}] Twilio auth failed - check Account SID and Auth Token", operation);
                    break;
                case 20404:
                    logger.error("❌ [{}] Twilio Verify Service not found - check TWILIO_VERIFY_SERVICE_SID", operation);
                    break;
                case 60200:
                    logger.warn("⚠️ [{}] Invalid phone number format: {}", operation, maskPhoneNumber(phoneNumber));
                    break;
                case 60203:
                    logger.warn("⚠️ [{}] Max verification attempts reached for: {}", operation, maskPhoneNumber(phoneNumber));
                    break;
                case 60202:
                    logger.warn("⚠️ [{}] No pending verification found for: {}", operation, maskPhoneNumber(phoneNumber));
                    break;
                case 60212:
                    logger.warn("⚠️ [{}] Verification expired for: {}", operation, maskPhoneNumber(phoneNumber));
                    break;
                default:
                    logger.error("❌ [{}] Twilio error {}: {} for: {}", 
                        operation, errorCode, errorMessage, maskPhoneNumber(phoneNumber));
            }
        } catch (Exception parseError) {
            logger.error("❌ [{}] Twilio error: {} for: {}", 
                operation, e.getMessage(), maskPhoneNumber(phoneNumber));
        }
    }
    
    /**
     * Pad string to the right for formatted logging.
     */
    private String padRight(String s, int n) {
        if (s == null) s = "";
        return String.format("%-" + n + "s", s);
    }
    
    /**
     * Check if the Twilio error is due to unverified number in trial account (error 21608).
     */
    private boolean isUnverifiedNumberError(HttpClientErrorException e) {
        try {
            JsonNode errorJson = objectMapper.readTree(e.getResponseBodyAsString());
            int errorCode = errorJson.path("code").asInt();
            return errorCode == 21608;
        } catch (Exception parseError) {
            return false;
        }
    }
    
    /**
     * Generate a fallback OTP for unverified numbers in trial mode.
     * Stores the OTP locally and logs it for development testing.
     */
    private boolean generateFallbackOtp(String normalizedPhone) {
        // Generate a random 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1000000));
        
        // Store it for verification
        fallbackOtpStorage.put(normalizedPhone, otp);
        
        // Log the OTP prominently for development use
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║  🔧 DEVELOPMENT FALLBACK OTP - UNVERIFIED NUMBER           ║");
        logger.info("╠════════════════════════════════════════════════════════════╣");
        logger.info("║ Phone: {}                                   ║", padRight(maskPhoneNumber(normalizedPhone), 18));
        logger.info("║                                                            ║");
        logger.info("║  ╔══════════════════════════════════════╗                  ║");
        logger.info("║  ║     YOUR OTP CODE: {}              ║                  ║", otp);
        logger.info("║  ╚══════════════════════════════════════╝                  ║");
        logger.info("║                                                            ║");
        logger.info("║ ⚠️  This number is not verified on Twilio trial account    ║");
        logger.info("║ ⚠️  Using local OTP for development testing only           ║");
        logger.info("║                                                            ║");
        logger.info("║ To send real SMS, verify this number at:                   ║");
        logger.info("║ https://console.twilio.com/us1/develop/phone-numbers/      ║");
        logger.info("║ manage/verified                                            ║");
        logger.info("╚════════════════════════════════════════════════════════════╝");
        
        return true;
    }
    
    /**
     * Check fallback OTP stored locally for unverified numbers.
     */
    private boolean checkFallbackOtp(String normalizedPhone, String code) {
        String storedOtp = fallbackOtpStorage.get(normalizedPhone);
        
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║  🔧 CHECKING FALLBACK OTP - UNVERIFIED NUMBER              ║");
        logger.info("╠════════════════════════════════════════════════════════════╣");
        logger.info("║ Phone: {}                                   ║", padRight(maskPhoneNumber(normalizedPhone), 18));
        logger.info("║ Code entered: {}                                    ║", padRight(code, 16));
        logger.info("║ Expected: {}                                        ║", padRight(storedOtp, 16));
        
        if (storedOtp != null && storedOtp.equals(code)) {
            logger.info("║                                                            ║");
            logger.info("║  ✅ OTP VERIFIED SUCCESSFULLY                              ║");
            logger.info("╚════════════════════════════════════════════════════════════╝");
            fallbackOtpStorage.remove(normalizedPhone);
            return true;
        } else {
            logger.info("║                                                            ║");
            logger.info("║  ❌ OTP VERIFICATION FAILED - INCORRECT CODE               ║");
            logger.info("╚════════════════════════════════════════════════════════════╝");
            return false;
        }
    }
}
