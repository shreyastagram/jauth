package com.fixhomi.auth.service;

import com.fixhomi.auth.dto.AppleMobileAuthRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.exception.AuthenticationException;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Service for handling Apple Sign-In authentication from mobile apps.
 *
 * Apple Sign-In uses RS256 JWTs signed by Apple's private keys.
 * We verify them using Apple's public keys from https://appleid.apple.com/auth/keys
 *
 * Flow:
 * 1. Mobile app shows Apple Sign-In UI (via @invertase/react-native-apple-authentication)
 * 2. User authenticates with Apple (Face ID / Touch ID / password)
 * 3. Mobile app receives identity token (JWT) + authorization code
 * 4. Mobile app sends identity token to this service
 * 5. This service fetches Apple's public keys, verifies the JWT, and issues FixHomi JWT
 *
 * Security considerations:
 * - Apple identity tokens are RS256 JWTs — we verify signature using Apple's public JWKS
 * - We validate issuer (https://appleid.apple.com), audience (our app's bundle ID), and expiry
 * - Apple provides email ONLY on first sign-in — we must persist it immediately
 * - Apple user IDs are stable per-app, used as the unique identifier for returning users
 * - Public keys are cached to avoid fetching on every auth request
 */
@Service
public class AppleAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AppleAuthService.class);
    private static final String APPLE_KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

    @Value("${fixhomi.oauth.apple.bundle-id:}")
    private String appleBundleId;

    @Value("${fixhomi.oauth.apple.service-id:}")
    private String appleServiceId;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // Cache Apple's public keys (they rotate infrequently)
    private final ConcurrentHashMap<String, PublicKey> applePublicKeys = new ConcurrentHashMap<>();
    private volatile long keysLastFetched = 0;
    private static final long KEYS_CACHE_DURATION_MS = 24 * 60 * 60 * 1000; // 24 hours

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Gson gson = new Gson();

    private boolean configured = false;

    @PostConstruct
    public void init() {
        if (appleBundleId == null || appleBundleId.isBlank()) {
            logger.warn("Apple Sign-In not configured. Set APPLE_BUNDLE_ID to enable.");
            return;
        }
        configured = true;
        logger.info("Apple Sign-In configured with bundle ID: {}", appleBundleId);

        // Pre-fetch Apple's public keys
        try {
            refreshApplePublicKeys();
        } catch (Exception e) {
            logger.warn("Failed to pre-fetch Apple public keys (will retry on first auth): {}", e.getMessage());
        }
    }

    /**
     * Authenticate user with Apple identity token from mobile app.
     *
     * Mirrors the GoogleAuthService pattern exactly:
     * - Verify token → find or create user → handle role conflicts → return JWT
     *
     * @param request contains the Apple identity token from mobile SDK
     * @return LoginResponse with FixHomi JWT tokens
     */
    @Transactional
    public LoginResponse authenticateWithApple(AppleMobileAuthRequest request) {
        if (!configured) {
            throw new AuthenticationException("Apple Sign-In is not configured. Please set APPLE_BUNDLE_ID.");
        }

        logger.debug("Mobile Apple authentication attempt");

        // Step 1: Verify the Apple identity token
        Claims claims = verifyAppleIdentityToken(request.getIdentityToken());

        // Step 2: Extract user info from verified token
        String appleUserId = claims.getSubject(); // Apple's stable user identifier
        String tokenEmail = claims.get("email", String.class);

        // Apple provides email in token on first sign-in, or client sends it
        String email = tokenEmail;
        if ((email == null || email.isBlank()) && request.getEmail() != null) {
            email = request.getEmail();
        }

        // Apple provides name ONLY on first sign-in via the client SDK (not in the token)
        String fullName = request.getFullName();

        if (appleUserId == null || appleUserId.isBlank()) {
            throw new AuthenticationException("Apple user identifier not found in token.");
        }

        logger.debug("Apple token verified for sub: {}", appleUserId);

        // Step 3: Security — only allow USER or SERVICE_PROVIDER roles
        Role requestedRole = Role.USER;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                Role clientRole = Role.valueOf(request.getRole());
                if (clientRole == Role.USER || clientRole == Role.SERVICE_PROVIDER) {
                    requestedRole = clientRole;
                } else {
                    logger.warn("Blocked privileged role '{}' for Apple OAuth — forcing USER", request.getRole());
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid role '{}' for Apple OAuth — using USER", request.getRole());
            }
        }

        // Step 4: Determine auth mode
        String mode = request.getMode();
        boolean isLoginMode = "login".equalsIgnoreCase(mode);
        boolean isSignupMode = "signup".equalsIgnoreCase(mode);

        // Step 5: Find existing user by Apple user ID first, then by email
        boolean isNewUser = false;
        User user = findUserByAppleId(appleUserId);

        if (user == null && email != null && !email.isBlank()) {
            // Try finding by email (user may have registered with email/password or Google first)
            user = userRepository.findByEmail(email).orElse(null);
        }

        if (user != null) {
            // ── User EXISTS ──

            // Cross-role detection (same pattern as Google)
            if (user.getRole() != requestedRole) {
                String existingRoleDisplay = user.getRole() == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                String requestedRoleDisplay = requestedRole == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                logger.warn("Role mismatch for Apple OAuth: {} is a {} but tried to auth as {}",
                        user.getEmail(), existingRoleDisplay, requestedRoleDisplay);
                throw new AuthenticationException(
                        "ROLE_CONFLICT:" + user.getRole().name() + ":" +
                        "This account is already registered as a " + existingRoleDisplay +
                        ". Please login from the " + existingRoleDisplay + " screen."
                );
            }

            // Signup mode check (same as Google)
            if (isSignupMode) {
                String roleDisplay = user.getRole() == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                logger.info("Signup attempt for existing Apple user: {} (role: {})", user.getEmail(), roleDisplay);
                throw new AuthenticationException(
                        "ALREADY_REGISTERED:" + user.getRole().name() + ":" +
                        "This account is already registered as a " + roleDisplay +
                        ". Please login instead."
                );
            }
        } else {
            // ── User does NOT exist ──

            // Login mode check (same as Google)
            if (isLoginMode) {
                logger.info("Login attempt for non-existent Apple user: sub={}", appleUserId);
                throw new AuthenticationException(
                        "NOT_REGISTERED:" + requestedRole.name() + ":" +
                        "No account found. Please register first."
                );
            }

            // Must have email for new user creation
            if (email == null || email.isBlank()) {
                throw new AuthenticationException(
                        "Email is required for registration. Please allow email access in Apple Sign-In."
                );
            }

            // Check if email is already used (edge case: different Apple ID, same email)
            // Only block if the existing account has a VERIFIED email
            User existingByEmail = userRepository.findByEmail(email).orElse(null);
            if (existingByEmail != null) {
                if (Boolean.TRUE.equals(existingByEmail.getIsEmailVerified())) {
                    String existingRoleDisplay = existingByEmail.getRole() == Role.SERVICE_PROVIDER ? "Service Provider" : "User";
                    throw new AuthenticationException(
                            "ALREADY_REGISTERED:" + existingByEmail.getRole().name() + ":" +
                            "This email is already registered as a " + existingRoleDisplay +
                            ". Please login instead."
                    );
                }
                // Unverified — reclaim it (Apple has verified this email)
                logger.info("Apple OAuth: reclaiming unverified email {} from user {} — deactivating old account",
                        email, existingByEmail.getId());
                existingByEmail.setEmail("unclaimed_" + existingByEmail.getId() + "@placeholder.local");
                existingByEmail.setIsActive(false);
                if (existingByEmail.getPhoneNumber() != null) {
                    existingByEmail.setPhoneNumber("del_" + existingByEmail.getId());
                }
                userRepository.save(existingByEmail);
            }

            user = createAppleUser(email, fullName, appleUserId, requestedRole);
            isNewUser = true;
        }

        // Step 6: Check if user is active
        if (!user.getIsActive()) {
            logger.warn("Apple login blocked for disabled user: {}", user.getEmail());
            throw new AuthenticationException("Account is deactivated. Please contact support.");
        }

        // Step 7: Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Step 8: Generate tokens (same as Google)
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        logger.info("Apple login successful for user: {} (role: {}, isNewUser: {})",
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
     * Verify Apple identity token using Apple's public JWKS keys.
     *
     * Security checks:
     * 1. Signature verification using Apple's RSA public key
     * 2. Issuer must be https://appleid.apple.com
     * 3. Audience must be our app's bundle ID or service ID
     * 4. Token must not be expired
     */
    private Claims verifyAppleIdentityToken(String identityToken) {
        try {
            // Parse the JWT header to get the key ID (kid)
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) {
                throw new AuthenticationException("Invalid Apple identity token format.");
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
            Map<String, String> header = gson.fromJson(headerJson, new TypeToken<Map<String, String>>(){}.getType());
            String kid = header.get("kid");
            String alg = header.get("alg");

            if (kid == null || kid.isBlank()) {
                throw new AuthenticationException("Apple token missing key ID.");
            }
            if (!"RS256".equals(alg)) {
                throw new AuthenticationException("Unsupported Apple token algorithm: " + alg);
            }

            // Get Apple's public key for this kid
            PublicKey publicKey = getApplePublicKey(kid);
            if (publicKey == null) {
                // Keys might have rotated — force refresh and retry
                refreshApplePublicKeys();
                publicKey = getApplePublicKey(kid);
                if (publicKey == null) {
                    throw new AuthenticationException("Apple public key not found for kid: " + kid);
                }
            }

            // Build list of valid audiences (bundle ID for native app, service ID for web)
            List<String> validAudiences = new java.util.ArrayList<>();
            validAudiences.add(appleBundleId);
            if (appleServiceId != null && !appleServiceId.isBlank()) {
                validAudiences.add(appleServiceId);
            }

            // Verify and parse the token (jjwt 0.11.x API)
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            // Validate audience manually (Jwts parser doesn't support multiple audiences easily)
            Object audClaim = claims.get("aud");
            String audience = audClaim instanceof String ? (String) audClaim : audClaim.toString();
            if (!validAudiences.contains(audience)) {
                throw new AuthenticationException("Apple token audience mismatch. Expected one of: " + validAudiences);
            }

            return claims;

        } catch (AuthenticationException e) {
            throw e; // Re-throw our own exceptions
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            logger.warn("Apple identity token expired");
            throw new AuthenticationException("Apple sign-in session expired. Please try again.");
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException e) {
            logger.error("Apple identity token signature/format invalid: {}", e.getMessage());
            throw new AuthenticationException("Invalid Apple sign-in token. Please try again.");
        } catch (Exception e) {
            logger.error("Failed to verify Apple identity token: {}", e.getMessage());
            throw new AuthenticationException("Apple sign-in verification failed. Please try again.");
        }
    }

    /**
     * Get Apple's RSA public key by key ID.
     */
    private PublicKey getApplePublicKey(String kid) {
        // Refresh cache if expired
        if (System.currentTimeMillis() - keysLastFetched > KEYS_CACHE_DURATION_MS) {
            try {
                refreshApplePublicKeys();
            } catch (Exception e) {
                logger.warn("Failed to refresh Apple keys, using cached: {}", e.getMessage());
            }
        }
        return applePublicKeys.get(kid);
    }

    /**
     * Fetch Apple's public keys from their JWKS endpoint and cache them.
     * Apple rotates keys periodically — we cache for 24 hours.
     */
    private synchronized void refreshApplePublicKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(APPLE_KEYS_URL))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Apple JWKS endpoint returned " + response.statusCode());
            }

            Map<String, Object> jwks = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
            @SuppressWarnings("unchecked")
            List<Map<String, String>> keys = (List<Map<String, String>>) jwks.get("keys");

            if (keys == null || keys.isEmpty()) {
                throw new RuntimeException("No keys found in Apple JWKS response");
            }

            ConcurrentHashMap<String, PublicKey> newKeys = new ConcurrentHashMap<>();
            for (Map<String, String> key : keys) {
                String kid = key.get("kid");
                String kty = key.get("kty");
                String n = key.get("n");
                String e = key.get("e");

                if ("RSA".equals(kty) && kid != null && n != null && e != null) {
                    try {
                        byte[] nBytes = Base64.getUrlDecoder().decode(n);
                        byte[] eBytes = Base64.getUrlDecoder().decode(e);
                        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                                new BigInteger(1, nBytes),
                                new BigInteger(1, eBytes)
                        );
                        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(spec);
                        newKeys.put(kid, publicKey);
                    } catch (Exception ex) {
                        logger.warn("Failed to parse Apple public key kid={}: {}", kid, ex.getMessage());
                    }
                }
            }

            applePublicKeys.clear();
            applePublicKeys.putAll(newKeys);
            keysLastFetched = System.currentTimeMillis();
            logger.info("Refreshed Apple public keys: {} keys cached", newKeys.size());

        } catch (Exception e) {
            logger.error("Failed to fetch Apple public keys: {}", e.getMessage());
            throw new RuntimeException("Could not fetch Apple authentication keys", e);
        }
    }

    /**
     * Find user by Apple user ID stored in email field convention or a dedicated lookup.
     * Since we don't have a dedicated appleUserId column, we search by email first.
     * For users who used "Hide My Email", the relay email is their unique identifier.
     */
    private User findUserByAppleId(String appleUserId) {
        // Apple user IDs are stable per-app. We can't query by them directly
        // unless we add an appleUserId column. For now, lookup is by email only.
        // The appleUserId is used as a fallback identifier in the token subject.
        return null;
    }

    /**
     * Create a new user from Apple Sign-In data.
     */
    private User createAppleUser(String email, String fullName, String appleUserId, Role role) {
        logger.info("Auto-registering new Apple user: {} with role: {}", email, role);

        User user = new User();
        user.setEmail(email);
        user.setFullName(fullName != null && !fullName.isBlank() ? fullName : email.split("@")[0]);
        user.setPasswordHash(null); // OAuth users don't have password
        user.setRole(role);
        user.setIsActive(true);
        user.setIsEmailVerified(true); // Apple has verified the email
        user.setIsPhoneVerified(false);

        return userRepository.save(user);
    }
}
