package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Brevo (formerly Sendinblue) implementation of EmailService.
 * Uses Brevo's SMTP API for sending transactional emails.
 * 
 * Active when: fixhomi.notification.email.provider=brevo
 * 
 * Required environment variables:
 * - BREVO_API_KEY: Your Brevo API key (v3)
 * - BREVO_SENDER_EMAIL: Verified sender email address
 * - BREVO_SENDER_NAME: Sender name (e.g., "FixHomi")
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.email.provider", havingValue = "brevo")
public class BrevoEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailService.class);
    
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${fixhomi.notification.email.brevo.api-key:}")
    private String apiKey;

    @Value("${fixhomi.notification.email.brevo.sender-email:noreply@fixhomi.com}")
    private String senderEmail;

    @Value("${fixhomi.notification.email.brevo.sender-name:FixHomi}")
    private String senderName;

    private final RestTemplate restTemplate;

    public BrevoEmailService() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean sendEmailVerification(String toEmail, String fullName, String verificationToken, String verificationUrl) {
        logger.info("📧 Sending email verification via Brevo to: {}", toEmail);
        
        // DEV MODE: Log verification link for testing with non-real emails
        logger.info("╔════════════════════════════════════════════════════════════╗");
        logger.info("║      📧 EMAIL VERIFICATION LINK (DEV MODE)                 ║");
        logger.info("╠════════════════════════════════════════════════════════════╣");
        logger.info("║  Email: {}", toEmail);
        logger.info("║  Token: {}", verificationToken);
        logger.info("║  Link: {}", verificationUrl);
        logger.info("╚════════════════════════════════════════════════════════════╝");
        
        String subject = "Verify your FixHomi email";
        String htmlContent = buildEmailTemplate(
            "Verify Your Email Address",
            String.format("Hi %s,", fullName),
            "Thank you for registering with FixHomi! Please verify your email address by clicking the button below:",
            "Verify Email",
            verificationUrl,
            "This link will expire in 24 hours. If you didn't create a FixHomi account, you can ignore this email."
        );
        
        return sendEmail(toEmail, fullName, subject, htmlContent);
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl) {
        logger.info("📧 Sending password reset email via Brevo to: {}", toEmail);
        
        String subject = "Reset your FixHomi password";
        String htmlContent = buildEmailTemplate(
            "Reset Your Password",
            String.format("Hi %s,", fullName),
            "We received a request to reset your password. Click the button below to create a new password:",
            "Reset Password",
            resetUrl,
            "This link will expire in 1 hour. If you didn't request a password reset, you can ignore this email."
        );
        
        return sendEmail(toEmail, fullName, subject, htmlContent);
    }

    @Override
    public boolean sendWelcomeEmail(String toEmail, String fullName) {
        logger.info("📧 Sending welcome email via Brevo to: {}", toEmail);
        
        String subject = "Welcome to FixHomi!";
        String htmlContent = buildEmailTemplate(
            "Welcome to FixHomi!",
            String.format("Hi %s,", fullName),
            "Thank you for joining FixHomi! We're excited to have you. You can now book home services from trusted professionals in your area.",
            "Get Started",
            "https://fixhomi.com",
            "If you have any questions, feel free to reach out to our support team."
        );
        
        return sendEmail(toEmail, fullName, subject, htmlContent);
    }

    @Override
    public boolean sendPasswordChangedNotification(String toEmail, String fullName) {
        logger.info("📧 Sending password changed notification via Brevo to: {}", toEmail);
        
        String subject = "Your FixHomi password was changed";
        String htmlContent = buildEmailTemplate(
            "Password Changed Successfully",
            String.format("Hi %s,", fullName),
            "Your FixHomi password has been successfully changed. If you made this change, no further action is needed.",
            null,
            null,
            "If you didn't change your password, please contact our support team immediately."
        );
        
        return sendEmail(toEmail, fullName, subject, htmlContent);
    }

    @Override
    public boolean sendLoginOtp(String toEmail, String fullName, String otp) {
        logger.info("📧 Sending login OTP via Brevo to: {}", toEmail);
        
        String subject = "Your FixHomi Login Code";
        String htmlContent = buildOtpEmailTemplate(
            "Your Login Code",
            String.format("Hi %s,", fullName),
            "Use this code to log in to your FixHomi account:",
            otp,
            "This code will expire in 5 minutes. If you didn't request this code, please ignore this email."
        );
        
        return sendEmail(toEmail, fullName, subject, htmlContent);
    }

    /**
     * Send email via Brevo API.
     */
    private boolean sendEmail(String toEmail, String toName, String subject, String htmlContent) {
        try {
            // Validate configuration
            if (apiKey == null || apiKey.isBlank()) {
                logger.error("❌ Brevo API key not configured");
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);
            headers.set("accept", "application/json");

            String jsonBody = String.format("""
                {
                    "sender": {
                        "name": "%s",
                        "email": "%s"
                    },
                    "to": [{
                        "email": "%s",
                        "name": "%s"
                    }],
                    "subject": "%s",
                    "htmlContent": "%s"
                }
                """,
                escapeJson(senderName),
                escapeJson(senderEmail),
                escapeJson(toEmail),
                escapeJson(toName != null ? toName : toEmail),
                escapeJson(subject),
                escapeJson(htmlContent)
            );

            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Email sent successfully to: {}", toEmail);
                return true;
            } else {
                logger.error("❌ Failed to send email. Status: {}, Body: {}", 
                    response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            logger.error("❌ Failed to send email via Brevo: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Build HTML email template with button.
     */
    private String buildEmailTemplate(String title, String greeting, String message, 
            String buttonText, String buttonUrl, String footer) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>");
        html.append("<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;'>");
        html.append("<h1 style='color: white; margin: 0;'>").append(escapeHtml(title)).append("</h1>");
        html.append("</div>");
        html.append("<div style='background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;'>");
        html.append("<p style='font-size: 16px; color: #333;'>").append(escapeHtml(greeting)).append("</p>");
        html.append("<p style='font-size: 16px; color: #666;'>").append(escapeHtml(message)).append("</p>");
        
        if (buttonText != null && buttonUrl != null) {
            html.append("<div style='text-align: center; margin: 30px 0;'>");
            html.append("<a href='").append(escapeHtml(buttonUrl)).append("' style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 15px 30px; text-decoration: none; border-radius: 25px; font-weight: bold; display: inline-block;'>")
                .append(escapeHtml(buttonText)).append("</a>");
            html.append("</div>");
        }
        
        html.append("<p style='font-size: 14px; color: #999;'>").append(escapeHtml(footer)).append("</p>");
        html.append("<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'>");
        html.append("<p style='font-size: 12px; color: #999; text-align: center;'>© 2026 FixHomi. All rights reserved.</p>");
        html.append("</div></body></html>");
        
        return html.toString();
    }

    /**
     * Build HTML email template with OTP code.
     */
    private String buildOtpEmailTemplate(String title, String greeting, String message, String otp, String footer) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>");
        html.append("<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; text-align: center; border-radius: 10px 10px 0 0;'>");
        html.append("<h1 style='color: white; margin: 0;'>").append(escapeHtml(title)).append("</h1>");
        html.append("</div>");
        html.append("<div style='background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;'>");
        html.append("<p style='font-size: 16px; color: #333;'>").append(escapeHtml(greeting)).append("</p>");
        html.append("<p style='font-size: 16px; color: #666;'>").append(escapeHtml(message)).append("</p>");
        
        html.append("<div style='text-align: center; margin: 30px 0;'>");
        html.append("<div style='background: #f0f0f0; padding: 20px; border-radius: 10px; display: inline-block;'>");
        html.append("<span style='font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #667eea;'>").append(escapeHtml(otp)).append("</span>");
        html.append("</div></div>");
        
        html.append("<p style='font-size: 14px; color: #999;'>").append(escapeHtml(footer)).append("</p>");
        html.append("<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'>");
        html.append("<p style='font-size: 12px; color: #999; text-align: center;'>© 2026 FixHomi. All rights reserved.</p>");
        html.append("</div></body></html>");
        
        return html.toString();
    }

    /**
     * Escape special characters for JSON string.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Escape special characters for HTML.
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
