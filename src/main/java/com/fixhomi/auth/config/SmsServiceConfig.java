package com.fixhomi.auth.config;

import com.fixhomi.auth.service.notification.Msg91SmsService;
import com.fixhomi.auth.service.notification.SmsService;
import com.fixhomi.auth.service.notification.StubSmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicitly creates the SmsService bean based on the provider property.
 * Replaces @ConditionalOnProperty which was unreliable in Docker/Render deployments.
 */
@Configuration
public class SmsServiceConfig {

    private static final Logger logger = LoggerFactory.getLogger(SmsServiceConfig.class);

    @Bean
    public SmsService smsService(
            @Value("${fixhomi.notification.sms.provider:stub}") String provider,
            @Value("${fixhomi.notification.sms.msg91.auth-key:}") String authKey,
            @Value("${fixhomi.notification.sms.msg91.template-id:}") String templateId,
            @Value("${fixhomi.notification.sms.msg91.verification-template-id:}") String verificationTemplateId,
            @Value("${fixhomi.notification.sms.msg91.delete-template-id:}") String deleteTemplateId) {

        logger.info("SMS provider property resolved to: '{}'", provider);

        if ("msg91".equalsIgnoreCase(provider.trim())) {
            logger.info("Creating Msg91SmsService for production SMS delivery");
            return new Msg91SmsService(authKey, templateId, verificationTemplateId, deleteTemplateId);
        }

        logger.info("Creating StubSmsService (provider='{}')", provider);
        return new StubSmsService();
    }
}
