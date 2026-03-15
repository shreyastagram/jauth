package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.PhoneLoginRequest;
import com.fixhomi.auth.dto.RegisterRequest;
import com.fixhomi.auth.entity.LoginLockout;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.exception.TooManyRequestsException;
import com.fixhomi.auth.repository.LoginLockoutRepository;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service for handling authentication operations.
 * Uses database-backed lockout tracking (survives server restarts)
 * with unified per-user lockout across all login methods.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    @Value("${fixhomi.auth.lockout.max-attempts:5}")
    private int maxFailedAttempts;

    @Value("${fixhomi.auth.lockout.duration-minutes:15}")
    private long lockoutDurationMinutes;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginLockoutRepository lockoutRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Authenticate user and generate JWT token.
     */
    @Transactional
    public LoginResponse login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail().toLowerCase();
        logger.debug("Login attempt for email: {}", email);

        // Check if account is locked due to failed attempts
        checkAccountLockout(email);

        // Find user by email
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> {
                    recordFailedAttempt(email, null);
                    return new AuthenticationException("Invalid email or password");
                });

        // Check unified lockout by userId (across all login methods)
        checkUnifiedLockout(user.getId());

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(email, user.getId());
            throw new AuthenticationException("Invalid email or password");
        }

        // Successful login — clear failed attempts for this identifier
        clearLockout(email);

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("User logged in successfully: {} (role: {})", user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified(),
                user.getIsEmailVerified()
        );
    }

    /**
     * Register a new user.
     * Public registration only allows USER and SERVICE_PROVIDER roles.
     */
    @Transactional
    public LoginResponse register(RegisterRequest registerRequest) {
        logger.debug("Registration attempt for email: {}", registerRequest.getEmail());

        // Validate role - public registration only allows USER and SERVICE_PROVIDER
        Role requestedRole = registerRequest.getRole();
        if (requestedRole != Role.USER && requestedRole != Role.SERVICE_PROVIDER) {
            logger.warn("Attempted registration with restricted role: {}", requestedRole);
            throw new InvalidRoleException(requestedRole.name(),
                    "Public registration only allows USER or SERVICE_PROVIDER roles");
        }

        // Check if email is verified on another active account
        String normalizedEmail = registerRequest.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailAndIsEmailVerifiedTrueAndIsActiveTrue(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", registerRequest.getEmail());
        }
        // If email exists but is unverified, the old account never confirmed ownership — reclaim it
        userRepository.findByEmailAndIsActiveTrue(normalizedEmail).ifPresent(oldUser -> {
            if (!Boolean.TRUE.equals(oldUser.getIsEmailVerified())) {
                logger.info("Reclaiming unverified email {} from user {} — deactivating old account",
                        normalizedEmail, oldUser.getId());
                oldUser.setEmail("unclaimed_" + oldUser.getId() + "@placeholder.local");
                oldUser.setIsActive(false);
                oldUser.setPhoneNumber(oldUser.getPhoneNumber() != null ? "del_" + oldUser.getId() : null);
                userRepository.save(oldUser);
            }
        });

        // Normalize and check if phone number is verified on another active account
        String normalizedPhone = User.normalizePhoneNumber(registerRequest.getPhoneNumber());
        if (normalizedPhone != null && !normalizedPhone.isBlank()) {
            if (userRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue(normalizedPhone)) {
                throw new DuplicateResourceException("User", "phoneNumber", registerRequest.getPhoneNumber());
            }
            // If phone exists but is unverified, clear it from old account so new user can claim
            userRepository.findByPhoneNumberAndIsActiveTrue(normalizedPhone).ifPresent(oldUser -> {
                if (!Boolean.TRUE.equals(oldUser.getIsPhoneVerified())) {
                    logger.info("Clearing unverified phone {} from user {} to allow new registration",
                            normalizedPhone, oldUser.getId());
                    oldUser.setPhoneNumber(null);
                    oldUser.setIsPhoneVerified(false);
                    userRepository.save(oldUser);
                }
            });
        }

        // Create new user (phone will be re-normalized by @PrePersist)
        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setPhoneNumber(normalizedPhone);
        user.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFullName(registerRequest.getFullName());
        user.setRole(registerRequest.getRole());
        user.setIsActive(true);
        user.setIsEmailVerified(false);
        user.setIsPhoneVerified(false);

        // Save user
        user = userRepository.save(user);

        logger.info("User registered successfully: {} (role: {})", user.getEmail(), user.getRole());

        // Generate JWT token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified(),
                user.getIsEmailVerified()
        );
    }

    /**
     * Authenticate user with phone number and password.
     */
    @Transactional
    public LoginResponse loginWithPhone(PhoneLoginRequest phoneLoginRequest) {
        String phone = User.normalizePhoneNumber(phoneLoginRequest.getPhoneNumber());
        logger.debug("Login attempt for phone: {}", maskPhoneNumber(phone));

        // Check if account is locked due to failed attempts (use phone as key)
        checkAccountLockout(phone);

        // Find user by phone number (normalized)
        User user = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> {
                    recordFailedAttempt(phone, null);
                    return new AuthenticationException("Invalid phone number or password");
                });

        // Check unified lockout by userId (across all login methods)
        checkUnifiedLockout(user.getId());

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(phoneLoginRequest.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(phone, user.getId());
            throw new AuthenticationException("Invalid phone number or password");
        }

        // Successful login — clear failed attempts
        clearLockout(phone);

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("User logged in via phone successfully: {} (role: {})",
                maskPhoneNumber(phoneLoginRequest.getPhoneNumber()), user.getRole());

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                user.getPhoneNumber(),
                user.getIsPhoneVerified(),
                user.getIsEmailVerified()
        );
    }

    /**
     * Mask phone number for logging (privacy protection).
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "***";
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "***" + phoneNumber.substring(len - 4);
    }

    /**
     * Check if an account (by email or phone) is locked out.
     * Uses database for persistent lockout tracking.
     */
    private void checkAccountLockout(String identifier) {
        lockoutRepository.findByIdentifier(identifier).ifPresent(lockout -> {
            if (lockout.isLocked()) {
                long remainingSeconds = java.time.Duration.between(
                    LocalDateTime.now(), lockout.getLockedUntil()).getSeconds();
                logger.warn("Login blocked for locked account: {} ({}s remaining)", identifier, remainingSeconds);
                throw new TooManyRequestsException(
                    "Too many failed attempts. Please try again in " + (remainingSeconds / 60 + 1) + " minutes.");
            }
        });
    }

    /**
     * Check unified lockout across all login methods for a user.
     * If any identifier for this userId is locked, block login on all methods.
     */
    private void checkUnifiedLockout(Long userId) {
        if (userId == null) return;
        List<LoginLockout> activeLocks = lockoutRepository.findActiveLocksForUser(userId, LocalDateTime.now());
        if (!activeLocks.isEmpty()) {
            LoginLockout lock = activeLocks.get(0);
            long remainingSeconds = java.time.Duration.between(
                LocalDateTime.now(), lock.getLockedUntil()).getSeconds();
            logger.warn("Login blocked by unified lockout for userId: {} ({}s remaining)", userId, remainingSeconds);
            throw new TooManyRequestsException(
                "Too many failed attempts. Please try again in " + (remainingSeconds / 60 + 1) + " minutes.");
        }
    }

    /**
     * Record a failed login attempt. Locks account after max attempts.
     * Persisted to database for durability across restarts.
     */
    private void recordFailedAttempt(String identifier, Long userId) {
        LoginLockout lockout = lockoutRepository.findByIdentifier(identifier)
                .orElse(new LoginLockout(identifier));

        // Reset if previous lockout has expired
        if (lockout.getLockedUntil() != null && !lockout.isLocked()) {
            lockout.resetAttempts();
        }

        lockout.setUserId(userId);
        lockout.incrementAttempts(maxFailedAttempts, lockoutDurationMinutes);
        lockoutRepository.save(lockout);

        if (lockout.isLocked()) {
            logger.warn("Account locked due to {} failed login attempts: {}",
                lockout.getFailedAttempts(), identifier);
        }
    }

    /**
     * Clear lockout for an identifier after successful login.
     */
    private void clearLockout(String identifier) {
        lockoutRepository.findByIdentifier(identifier).ifPresent(lockout -> {
            lockout.resetAttempts();
            lockoutRepository.save(lockout);
        });
    }

    /**
     * Cleanup expired lockout entries every 30 minutes.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    @Transactional
    public void cleanupExpiredLockouts() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(lockoutDurationMinutes * 2);
        int removed = lockoutRepository.deleteExpiredLockouts(cutoff);
        if (removed > 0) {
            logger.info("Cleaned up {} expired login lockout entries", removed);
        }
    }
}
