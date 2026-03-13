package com.fixhomi.auth.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * MSG91 Flow API implementation of SmsService.
 *
 * Uses MSG91's Flow (transactional SMS) API to deliver OTP messages.
 * OTP generation and verification is handled locally by the calling service.
 * MSG91 is only responsible for SMS delivery.
 *
 * Bean is created by SmsServiceConfig when fixhomi.notification.sms.provider=msg91.
 *
 * Required environment variables:
 * - MSG91_AUTH_KEY: Your MSG91 auth key
 * - MSG91_TEMPLATE_ID: Template ID with ##OTP## placeholder
 */
public class Msg91SmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(Msg91SmsService.class);

    private static final String MSG91_FLOW_URL = "https://control.msg91.com/api/v5/flow";

    private final String authKey;
    private final String templateId;
    private final String verificationTemplateId;
    private final String deleteTemplateId;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Msg91SmsService(String authKey, String templateId,
                           String verificationTemplateId, String deleteTemplateId) {
        this.authKey = authKey;
        this.templateId = templateId;
        this.verificationTemplateId = verificationTemplateId;
        this.deleteTemplateId = deleteTemplateId;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();

        // Validate on construction
        if (authKey == null || authKey.isBlank()) {
            logger.error("MSG91_AUTH_KEY is not configured!");
        }
        if (templateId == null || templateId.isBlank()) {
            logger.error("MSG91_TEMPLATE_ID is not configured!");
        }
        if (authKey != null && !authKey.isBlank() && templateId != null && !templateId.isBlank()) {
            logger.info("MSG91 SMS Service configured — template: {}...",
                templateId.substring(0, Math.min(8, templateId.length())));
        }
    }

    /**
     * Send OTP via MSG91 Flow API.
     * The OTP is passed as a template variable (##OTP## in the template).
     *
     * POST https://control.msg91.com/api/v5/flow/
     * Headers: authkey, Content-Type: application/json
     * Body: { "template_id": "...", "short_url": "0",
     *         "recipients": [{ "mobiles": "919876543210", "OTP": "123456" }] }
     *
     * @param phoneNumber recipient phone number (any format — will be normalized)
     * @param otp the OTP code to include in the SMS
     * @return true if SMS was sent successfully
     */
    @Override
    public boolean sendOtp(String phoneNumber, String otp) {
        return sendOtp(phoneNumber, otp, templateId);
    }

    @Override
    public boolean sendOtp(String phoneNumber, String otp, String overrideTemplateId) {
        String normalizedPhone = normalizePhone(phoneNumber);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            logger.warn("Invalid phone number provided for MSG91 OTP");
            return false;
        }

        // Use override template if provided, otherwise fall back to default
        String effectiveTemplateId = (overrideTemplateId != null && !overrideTemplateId.isBlank())
                ? overrideTemplateId : templateId;

        logger.info("Sending OTP via MSG91 Flow API to: {} (template: {}...)",
                maskPhoneNumber(normalizedPhone),
                effectiveTemplateId.substring(0, Math.min(8, effectiveTemplateId.length())));

        try {
            validateConfig(effectiveTemplateId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("authkey", authKey);
            headers.set("accept", "application/json");
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Build Flow API request body
            ObjectNode recipient = objectMapper.createObjectNode();
            recipient.put("mobiles", normalizedPhone);
            recipient.put("OTP", otp);

            ArrayNode recipients = objectMapper.createArrayNode();
            recipients.add(recipient);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("template_id", effectiveTemplateId);
            body.put("short_url", "0");
            body.set("recipients", recipients);

            HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(MSG91_FLOW_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                if (responseBody == null) {
                    logger.warn("MSG91 Flow API returned empty response");
                    return false;
                }

                JsonNode json = objectMapper.readTree(responseBody);
                String type = json.path("type").asText("");

                if ("success".equalsIgnoreCase(type)) {
                    logger.info("OTP SMS sent successfully via MSG91 to: {}", maskPhoneNumber(normalizedPhone));
                    return true;
                } else {
                    String message = json.path("message").asText("Unknown error");
                    logger.warn("MSG91 Flow API failed: {}", message);
                    return false;
                }
            } else {
                logger.error("MSG91 Flow API HTTP error: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            handleApiError(e, "SendOTP", normalizedPhone);
            return false;
        } catch (HttpServerErrorException e) {
            logger.error("MSG91 Flow API server error ({}) for: {}", e.getStatusCode(), maskPhoneNumber(normalizedPhone));
            return false;
        } catch (Exception e) {
            logger.error("MSG91 Flow API failed for {}: {}", maskPhoneNumber(normalizedPhone), e.getMessage());
            return false;
        }
    }

    /**
     * Get the verification template ID (for phone verification).
     */
    public String getVerificationTemplateId() {
        return verificationTemplateId;
    }

    /**
     * Get the delete account template ID.
     */
    public String getDeleteTemplateId() {
        return deleteTemplateId;
    }

    @Override
    public boolean sendVerificationSuccess(String phoneNumber) {
        logger.debug("Phone verified: {} (success SMS skipped)", maskPhoneNumber(phoneNumber));
        return true;
    }

    /**
     * Handle MSG91 API errors with useful logging.
     */
    private void handleApiError(HttpClientErrorException e, String operation, String phone) {
        try {
            String body = e.getResponseBodyAsString();
            JsonNode json = objectMapper.readTree(body);
            String message = json.path("message").asText("Unknown error");
            logger.error("MSG91 {} error ({}) for {}: {}", operation, e.getStatusCode(), maskPhoneNumber(phone), message);
        } catch (Exception parseErr) {
            logger.error("MSG91 {} error ({}) for {}", operation, e.getStatusCode(), maskPhoneNumber(phone));
        }
    }

    /**
     * Validate MSG91 configuration.
     */
    private void validateConfig(String effectiveTemplateId) {
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("MSG91 Auth Key is not configured");
        }
        if (effectiveTemplateId == null || effectiveTemplateId.isBlank()) {
            throw new IllegalStateException("MSG91 Template ID is not configured");
        }
    }

    /**
     * Normalize phone number to MSG91 format.
     * MSG91 expects country code + number without + prefix (e.g., 919876543210).
     */
    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        String cleaned = phoneNumber.trim().replaceAll("[^0-9]", "");

        if (cleaned.isEmpty()) {
            return null;
        }

        // 10-digit Indian number -> prepend 91
        if (cleaned.length() == 10) {
            return "91" + cleaned;
        }

        // 0-prefix STD code -> strip 0, prepend 91
        if (cleaned.length() == 11 && cleaned.startsWith("0")) {
            return "91" + cleaned.substring(1);
        }

        // Already has country code
        return cleaned;
    }

    /**
     * Mask phone number for logging.
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
