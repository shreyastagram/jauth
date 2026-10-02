package com.fixhomi.auth.config;

import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a single ADMIN account for local development.
 *
 * Public registration only allows USER / SERVICE_PROVIDER, and elevated roles
 * (ADMIN, IT_ADMIN, SUPPORT) can only be created by an existing ADMIN/IT_ADMIN
 * via POST /api/admin/users. This seeder provides that first admin on a fresh
 * local database.
 *
 * Active ONLY with the "local" Spring profile AND
 * fixhomi.local.seed-admin.enabled=true (both set in application-local.yaml).
 * Idempotent: does nothing if the email already exists.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "fixhomi.local.seed-admin", name = "enabled", havingValue = "true")
public class LocalDevDataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LocalDevDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${fixhomi.local.seed-admin.email}")
    private String adminEmail;

    @Value("${fixhomi.local.seed-admin.password}")
    private String adminPassword;

    @Value("${fixhomi.local.seed-admin.full-name:Local Admin}")
    private String adminFullName;

    public LocalDevDataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            logger.info("[local] Seed admin already exists: {}", email);
            return;
        }

        User admin = new User(email, passwordEncoder.encode(adminPassword), adminFullName, Role.ADMIN);
        admin.setIsActive(true);
        admin.setIsEmailVerified(true);
        userRepository.save(admin);

        logger.info("[local] Seeded ADMIN account: {} (password from fixhomi.local.seed-admin.password)", email);
    }
}
