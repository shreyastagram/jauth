package com.fixhomi.auth.config;

import com.fixhomi.auth.service.notification.BrevoEmailService;
import com.fixhomi.auth.service.notification.EmailService;
import com.fixhomi.auth.service.notification.StubEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly creates the EmailService bean based on the provider property.
 * Replaces @ConditionalOnProperty which was unreliable in Docker/Render deployments.
 */
@Configuration
public class EmailServiceConfig {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceConfig.class);

    @Bean
    public EmailService emailService(
            @Value("${fixhomi.notification.email.provider:stub}") String provider,
            @Value("${fixhomi.notification.email.brevo.api-key:}") String apiKey,
            @Value("${fixhomi.notification.email.brevo.sender-email:noreply@fixhomi.com}") String senderEmail,
            @Value("${fixhomi.notification.email.brevo.sender-name:FixHomi}") String senderName) {

        logger.info("Email provider property resolved to: '{}'", provider);

        if ("brevo".equalsIgnoreCase(provider.trim())) {
            logger.info("Creating BrevoEmailService for production email delivery");
            return new BrevoEmailService(apiKey, senderEmail, senderName);
        }

        logger.info("Creating StubEmailService (provider='{}')", provider);
        return new StubEmailService();
    }
}
