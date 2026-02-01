package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.GoogleMobileAuthRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/**
 * Service for handling Google OAuth authentication from mobile apps.
 * 
 * Mobile apps use Google Sign-In SDK which returns an ID token.
 * This service verifies the token with Google and issues FixHomi JWT tokens.
 * 
 * This is the industry-standard approach for mobile OAuth:
 * 1. Mobile app shows Google Sign-In UI
 * 2. User authenticates with Google
 * 3. Mobile app receives Google ID token
 * 4. Mobile app sends ID token to this service
 * 5. This service verifies token and returns FixHomi JWT
 */
@Service
public class GoogleAuthService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleAuthService.class);

    @Value("${spring.security.oauth2.client.registration.google.client-id:}")
    private String googleClientId;

    @Value("${fixhomi.oauth.google.ios-client-id:}")
    private String iosClientId;

    @Value("${fixhomi.oauth.google.android-client-id:}")
    private String androidClientId;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        // Build list of valid client IDs (web, iOS, Android)
        // All these should be from the same Google Cloud project
        var clientIds = Arrays.asList(
            googleClientId,
            iosClientId.isBlank() ? googleClientId : iosClientId,
            androidClientId.isBlank() ? googleClientId : androidClientId
        ).stream().filter(id -> id != null && !id.isBlank()).distinct().toList();

        if (clientIds.isEmpty() || clientIds.get(0).equals("your-google-client-id")) {
            logger.warn("Google OAuth client IDs not configured. Mobile Google Sign-In will not work.");
            verifier = null;
            return;
        }

        verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), 
                GsonFactory.getDefaultInstance())
            .setAudience(clientIds)
            .build();

        logger.info("Google ID token verifier initialized with {} client ID(s)", clientIds.size());
    }

    /**
     * Authenticate user with Google ID token from mobile app.
     * 
     * @param request contains the Google ID token from mobile SDK
     * @return LoginResponse with FixHomi JWT tokens
     * @throws AuthenticationException if token is invalid or Google auth fails
     */
    @Transactional
    public LoginResponse authenticateWithGoogle(GoogleMobileAuthRequest request) {
        if (verifier == null) {
            throw new AuthenticationException("Google Sign-In is not configured. Please set GOOGLE_CLIENT_ID.");
        }

        logger.debug("Mobile Google authentication attempt");

        // Verify the Google ID token
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.getIdToken());
        } catch (Exception e) {
            logger.error("Failed to verify Google ID token: {}", e.getMessage());
            throw new AuthenticationException("Invalid Google token. Please try signing in again.");
        }

        if (idToken == null) {
            logger.warn("Google ID token verification returned null");
            throw new AuthenticationException("Invalid or expired Google token. Please try signing in again.");
        }

        // Extract user info from verified token
        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        Boolean emailVerified = payload.getEmailVerified();

        if (email == null || email.isBlank()) {
            throw new AuthenticationException("Email not provided by Google. Please grant email permission.");
        }

        if (emailVerified != null && !emailVerified) {
            throw new AuthenticationException("Email not verified with Google. Please verify your email first.");
        }

        logger.debug("Google token verified for email: {}", email);

        // Determine requested role for new users
        Role requestedRole = Role.USER; // Default
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                requestedRole = Role.valueOf(request.getRole());
                // Only allow USER and SERVICE_PROVIDER for self-registration
                if (requestedRole != Role.USER && requestedRole != Role.SERVICE_PROVIDER) {
                    logger.warn("Invalid role requested via Google OAuth: {}, defaulting to USER", request.getRole());
                    requestedRole = Role.USER;
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid role string: {}, defaulting to USER", request.getRole());
                requestedRole = Role.USER;
            }
        }

        // Find existing user or create new one
        final Role finalRole = requestedRole;
        User user = userRepository.findByEmail(email).orElse(null);
        
        boolean isNewUser = false;
        
        if (user != null) {
            // SECURITY CHECK: Verify user role matches the requested role
            // This prevents a USER from accessing Provider screens and vice versa
            if (user.getRole() != finalRole) {
                String existingRoleDisplay = user.getRole() == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                String requestedRoleDisplay = finalRole == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                logger.warn("Role mismatch for Google OAuth: {} is a {} but tried to login as {}", 
                    email, existingRoleDisplay, requestedRoleDisplay);
                throw new AuthenticationException(
                    "ROLE_CONFLICT: This email is already registered as a " + existingRoleDisplay + 
                    ". Please login from the correct app screen."
                );
            }
        } else {
            // Create new user with the requested role
            user = createGoogleUser(email, name, pictureUrl, finalRole);
            isNewUser = true;
        }

        // Check if user is active
        if (!user.getIsActive()) {
            logger.warn("Mobile Google login blocked for disabled user: {}", email);
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT access token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("Mobile Google login successful for user: {} (role: {}, isNewUser: {})", 
                user.getEmail(), user.getRole(), isNewUser);

        return new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds(),
                isNewUser
        );
    }

    /**
     * Create a new user from Google Sign-In data.
     * @param email User's email from Google
     * @param name User's name from Google
     * @param pictureUrl User's profile picture URL
     * @param role Role for the new user (USER or SERVICE_PROVIDER)
     */
    private User createGoogleUser(String email, String name, String pictureUrl, Role role) {
        logger.info("Auto-registering new Google user from mobile: {} with role: {}", email, role);

        User user = new User();
        user.setEmail(email);
        user.setFullName(name != null ? name : email.split("@")[0]);
        user.setPasswordHash(null); // OAuth users don't have password
        user.setRole(role);
        user.setIsActive(true);
        user.setIsEmailVerified(true); // Google has verified the email
        user.setIsPhoneVerified(false);
        // Note: Profile picture URL could be stored if User entity has a field for it

        return userRepository.save(user);
    }
}
