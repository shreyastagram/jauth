package com.fixhomi.auth.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Brevo (formerly Sendinblue) implementation of EmailService.
 * 
 * Active when: fixhomi.notification.email.provider=brevo
 * 
 * TODO: Implement actual Brevo API integration.
 * Required dependency: com.sendinblue:sib-api-v3-sdk
 */
@Service
@ConditionalOnProperty(name = "fixhomi.notification.email.provider", havingValue = "brevo")
public class BrevoEmailService implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(BrevoEmailService.class);

    @Value("${fixhomi.notification.email.brevo.api-key:}")
    private String apiKey;

    @Value("${fixhomi.notification.email.brevo.sender-email:noreply@fixhomi.com}")
    private String senderEmail;

    @Value("${fixhomi.notification.email.brevo.sender-name:FixHomi}")
    private String senderName;

    @Override
    public boolean sendEmailVerification(String toEmail, String fullName, String verificationToken, String verificationUrl) {
        logger.info("Sending email verification via Brevo to: {}", toEmail);
        
        // TODO: Implement Brevo API call
        // TransactionalEmailsApi api = new TransactionalEmailsApi();
        // SendSmtpEmail email = new SendSmtpEmail();
        // email.setSender(new SendSmtpEmailSender().email(senderEmail).name(senderName));
        // email.setTo(List.of(new SendSmtpEmailTo().email(toEmail).name(fullName)));
        // email.setSubject("Verify your FixHomi email");
        // email.setHtmlContent("...");
        // api.sendTransacEmail(email);
        
        logger.warn("Brevo email service not fully implemented yet. Token: {}", verificationToken);
        return false;
    }

    @Override
    public boolean sendPasswordResetEmail(String toEmail, String fullName, String resetToken, String resetUrl) {
        logger.info("Sending password reset email via Brevo to: {}", toEmail);
        
        // TODO: Implement Brevo API call
        logger.warn("Brevo email service not fully implemented yet. Token: {}", resetToken);
        return false;
    }

    @Override
    public boolean sendWelcomeEmail(String toEmail, String fullName) {
        logger.info("Sending welcome email via Brevo to: {}", toEmail);
        
        // TODO: Implement Brevo API call
        logger.warn("Brevo email service not fully implemented yet.");
        return false;
    }

    @Override
    public boolean sendPasswordChangedNotification(String toEmail, String fullName) {
        logger.info("Sending password changed notification via Brevo to: {}", toEmail);
        
        // TODO: Implement Brevo API call
        logger.warn("Brevo email service not fully implemented yet.");
        return false;
    }
}
