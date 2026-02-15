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

/**
 * Service for handling authentication operations.
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

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
        logger.debug("Login attempt for email: {}", loginRequest.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new AuthenticationException("Invalid email or password"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

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
        logger.debug("Login attempt for phone: {}", maskPhoneNumber(phoneLoginRequest.getPhoneNumber()));

        // Find user by phone number
        User user = userRepository.findByPhoneNumber(phoneLoginRequest.getPhoneNumber())
                .orElseThrow(() -> new AuthenticationException("Invalid phone number or password"));

        // Check if user is active
        if (!user.getIsActive()) {
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(phoneLoginRequest.getPassword(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid phone number or password");
        }

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
}
