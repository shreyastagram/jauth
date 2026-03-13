package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.LoginRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.PhoneLoginRequest;
import com.fixhomi.auth.dto.RegisterRequest;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.exception.DuplicateResourceException;
import com.fixhomi.auth.exception.InvalidRoleException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Service for handling authentication operations.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    // Per-email failed login attempt tracking (brute force protection)
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutes
    private static final ConcurrentHashMap<String, FailedLoginTracker> failedAttempts = new ConcurrentHashMap<>();

    private static class FailedLoginTracker {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong lockedUntil = new AtomicLong(0); // epoch ms, 0 = not locked
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Authenticate user and generate JWT token.
     *
     * @param loginRequest login credentials
     * @return login response with JWT token
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
                    recordFailedAttempt(email);
                    return new AuthenticationException("Invalid email or password");
                });

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(email);
            throw new AuthenticationException("Invalid email or password");
        }

        // Successful login — clear failed attempts
        failedAttempts.remove(email);

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
                user.getIsPhoneVerified()
        );
    }

    /**
     * Register a new user.
     * Public registration only allows USER and SERVICE_PROVIDER roles.
     *
     * @param registerRequest registration details
     * @return login response with JWT token
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

        // Check if email already exists
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new DuplicateResourceException("User", "email", registerRequest.getEmail());
        }

        // Check if phone number already exists (if provided)
        if (registerRequest.getPhoneNumber() != null && 
            !registerRequest.getPhoneNumber().isBlank() &&
            userRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())) {
            throw new DuplicateResourceException("User", "phoneNumber", registerRequest.getPhoneNumber());
        }

        // Create new user
        User user = new User();
        user.setEmail(registerRequest.getEmail());
        user.setPhoneNumber(registerRequest.getPhoneNumber());
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
                user.getIsPhoneVerified()
        );
    }

    /**
     * Authenticate user with phone number and password.
     *
     * @param phoneLoginRequest login credentials with phone number
     * @return login response with JWT token
     */
    @Transactional
    public LoginResponse loginWithPhone(PhoneLoginRequest phoneLoginRequest) {
        String phone = phoneLoginRequest.getPhoneNumber();
        logger.debug("Login attempt for phone: {}", maskPhoneNumber(phone));

        // Check if account is locked due to failed attempts (use phone as key)
        checkAccountLockout(phone);

        // Find user by phone number
        User user = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> {
                    recordFailedAttempt(phone);
                    return new AuthenticationException("Invalid phone number or password");
                });

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(phoneLoginRequest.getPassword(), user.getPasswordHash())) {
            recordFailedAttempt(phone);
            throw new AuthenticationException("Invalid phone number or password");
        }

        // Successful login — clear failed attempts
        failedAttempts.remove(phone);

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
                user.getIsPhoneVerified()
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
     */
    private void checkAccountLockout(String key) {
        FailedLoginTracker tracker = failedAttempts.get(key);
        if (tracker != null) {
            long lockedUntil = tracker.lockedUntil.get();
            if (lockedUntil > System.currentTimeMillis()) {
                long remainingSeconds = (lockedUntil - System.currentTimeMillis()) / 1000;
                logger.warn("Login blocked for locked account: {} ({}s remaining)", key, remainingSeconds);
                throw new com.fixhomi.auth.exception.TooManyRequestsException(
                    "Too many failed attempts. Please try again in " + (remainingSeconds / 60 + 1) + " minutes.");
            }
        }
    }

    /**
     * Record a failed login attempt. Locks account after MAX_FAILED_ATTEMPTS.
     */
    private void recordFailedAttempt(String key) {
        FailedLoginTracker tracker = failedAttempts.computeIfAbsent(key, k -> new FailedLoginTracker());
        // Reset if previous lockout has expired
        long lockedUntil = tracker.lockedUntil.get();
        if (lockedUntil > 0 && lockedUntil < System.currentTimeMillis()) {
            tracker.count.set(0);
            tracker.lockedUntil.set(0);
        }
        int newCount = tracker.count.incrementAndGet();
        if (newCount >= MAX_FAILED_ATTEMPTS) {
            tracker.lockedUntil.set(System.currentTimeMillis() + LOCKOUT_DURATION_MS);
            logger.warn("Account locked due to {} failed login attempts: {}", newCount, key);
        }
    }

    /**
     * Cleanup expired lockout entries every 30 minutes.
     * Prevents unbounded growth of the failedAttempts map.
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupExpiredLockouts() {
        long now = System.currentTimeMillis();
        int sizeBefore = failedAttempts.size();
        failedAttempts.entrySet().removeIf(entry -> {
            FailedLoginTracker tracker = entry.getValue();
            long lockedUntil = tracker.lockedUntil.get();
            // Remove if lockout has expired or if there's no lockout and count is stale
            return (lockedUntil > 0 && lockedUntil < now) ||
                   (lockedUntil == 0 && tracker.count.get() < MAX_FAILED_ATTEMPTS);
        });
        int removed = sizeBefore - failedAttempts.size();
        if (removed > 0) {
            logger.info("Cleaned up {} expired login lockout entries", removed);
        }
    }
}
