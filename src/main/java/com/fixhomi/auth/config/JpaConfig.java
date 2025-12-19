package com.fixhomi.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration to enable auditing.
 * This allows automatic population of @CreatedDate and @LastModifiedDate fields.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
