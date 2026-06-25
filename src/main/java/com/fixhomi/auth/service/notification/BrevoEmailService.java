package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

/**
 * Brevo (formerly Sendinblue) implementation of EmailService.
 * Uses Brevo's SMTP API for sending transactional emails.
 *
 * Bean is created by EmailServiceConfig when fixhomi.notification.email.provider=brevo.
 *
 * Required environment variables:
 * - BREVO_API_KEY: Your Brevo API key (v3)
 * - BREVO_SENDER_EMAIL: Verified sender email address
 * - BREVO_SENDER_NAME: Sender name (e.g., "FixHomi")
 */
public class BrevoEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailService.class);

    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final RestTemplate restTemplate;

    public BrevoEmailService(String apiKey, String senderEmail, String senderName) {
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
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

    // FixHomi brand palette (matches the app)
    private static final String BRAND = "#f67c16";        // primary orange
    private static final String INK = "#1E293B";          // headings
    private static final String BODY_TEXT = "#475569";    // paragraph text
    private static final String MUTED = "#94A3B8";        // footnotes
    private static final String CARD_BORDER = "#E2E8F0";
    private static final String PAGE_BG = "#F1F5F9";
    private static final String FOOTER_BG = "#F8FAFC";

    /**
     * Build HTML email template with a primary call-to-action button.
     */
    private String buildEmailTemplate(String title, String greeting, String message,
            String buttonText, String buttonUrl, String footer) {
        String button = "";
        if (buttonText != null && buttonUrl != null) {
            // "Bulletproof" button — table cell carries the colour so it renders in
            // every client (Outlook included), not a styled <a> that some clients strip.
            button =
                "<table role='presentation' cellpadding='0' cellspacing='0' style='margin: 28px auto;'>" +
                  "<tr><td align='center' bgcolor='" + BRAND + "' style='border-radius: 12px;'>" +
                    "<a href='" + escapeHtml(buttonUrl) + "' target='_blank' " +
                       "style='display: inline-block; padding: 14px 36px; font-family: Arial, Helvetica, sans-serif; " +
                       "font-size: 16px; font-weight: bold; color: #ffffff; text-decoration: none; border-radius: 12px;'>" +
                       escapeHtml(buttonText) +
                    "</a>" +
                  "</td></tr>" +
                "</table>";
        }
        String body =
            "<p style='margin: 0 0 14px; font-size: 16px; font-weight: 600; color: " + INK + ";'>" + escapeHtml(greeting) + "</p>" +
            "<p style='margin: 0; font-size: 15px; line-height: 24px; color: " + BODY_TEXT + ";'>" + escapeHtml(message) + "</p>" +
            button +
            "<p style='margin: 18px 0 0; font-size: 13px; line-height: 20px; color: " + MUTED + ";'>" + escapeHtml(footer) + "</p>";
        return baseShell(title, body);
    }

    /**
     * Build HTML email template with a one-time code.
     */
    private String buildOtpEmailTemplate(String title, String greeting, String message, String otp, String footer) {
        String otpBox =
            "<table role='presentation' cellpadding='0' cellspacing='0' style='margin: 26px auto;'>" +
              "<tr><td align='center' style='background-color: #FFF7ED; border: 1px solid #FED7AA; border-radius: 12px; padding: 18px 30px;'>" +
                "<span style='font-family: Arial, Helvetica, sans-serif; font-size: 34px; font-weight: bold; letter-spacing: 10px; color: " + BRAND + ";'>" +
                  escapeHtml(otp) +
                "</span>" +
              "</td></tr>" +
            "</table>";
        String body =
            "<p style='margin: 0 0 14px; font-size: 16px; font-weight: 600; color: " + INK + ";'>" + escapeHtml(greeting) + "</p>" +
            "<p style='margin: 0; font-size: 15px; line-height: 24px; color: " + BODY_TEXT + ";'>" + escapeHtml(message) + "</p>" +
            otpBox +
            "<p style='margin: 18px 0 0; font-size: 13px; line-height: 20px; color: " + MUTED + ";'>" + escapeHtml(footer) + "</p>";
        return baseShell(title, body);
    }

    /**
     * Shared, brand-consistent email shell: page background, centered white card,
     * orange FixHomi header, title, body slot, and footer. Table-based + inline
     * styles for maximum email-client compatibility.
     */
    private String baseShell(String title, String bodyHtml) {
        return
            "<!DOCTYPE html>" +
            "<html lang='en'><head>" +
              "<meta charset='UTF-8'>" +
              "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
              "<meta name='color-scheme' content='light only'>" +
            "</head>" +
            "<body style='margin: 0; padding: 0; background-color: " + PAGE_BG + ";'>" +
              "<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='background-color: " + PAGE_BG + "; padding: 24px 12px;'>" +
                "<tr><td align='center'>" +
                  "<table role='presentation' width='600' cellpadding='0' cellspacing='0' style='max-width: 600px; width: 100%; background-color: #ffffff; border-radius: 16px; overflow: hidden; border: 1px solid " + CARD_BORDER + ";'>" +
                    // Header — brand wordmark
                    "<tr><td align='center' style='background-color: " + BRAND + "; padding: 26px 24px;'>" +
                      "<div style='font-family: Arial, Helvetica, sans-serif; font-size: 24px; font-weight: 800; letter-spacing: 0.5px; color: #ffffff;'>FixHomi</div>" +
                      "<div style='font-family: Arial, Helvetica, sans-serif; font-size: 12px; color: #FFE6CC; margin-top: 4px;'>Home services, simplified</div>" +
                    "</td></tr>" +
                    // Title
                    "<tr><td style='padding: 30px 36px 0;'>" +
                      "<h1 style='margin: 0; font-family: Arial, Helvetica, sans-serif; font-size: 21px; font-weight: 700; color: " + INK + ";'>" + escapeHtml(title) + "</h1>" +
                    "</td></tr>" +
                    // Body
                    "<tr><td style='padding: 14px 36px 32px; font-family: Arial, Helvetica, sans-serif;'>" + bodyHtml + "</td></tr>" +
                    // Footer
                    "<tr><td style='background-color: " + FOOTER_BG + "; padding: 22px 36px; border-top: 1px solid " + CARD_BORDER + ";'>" +
                      "<p style='margin: 0; font-family: Arial, Helvetica, sans-serif; font-size: 12px; line-height: 18px; color: " + MUTED + "; text-align: center;'>© 2026 FixHomi. All rights reserved.</p>" +
                      "<p style='margin: 6px 0 0; font-family: Arial, Helvetica, sans-serif; font-size: 11px; line-height: 16px; color: #CBD5E1; text-align: center;'>This is an automated message — please do not reply.</p>" +
                    "</td></tr>" +
                  "</table>" +
                "</td></tr>" +
              "</table>" +
            "</body></html>";
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
