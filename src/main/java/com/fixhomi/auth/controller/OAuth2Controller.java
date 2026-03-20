package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.AppleMobileAuthRequest;
import com.fixhomi.auth.dto.GoogleMobileAuthRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.service.AppleAuthService;
import com.fixhomi.auth.service.AppleEmailVerificationService;
import com.fixhomi.auth.service.GoogleAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for OAuth2 authentication endpoints.
 * Provides mobile-specific OAuth flows for React Native and other mobile clients.
 */
@RestController
@RequestMapping("/api/auth/oauth2")
@Tag(name = "OAuth2 Authentication", description = "OAuth2 authentication endpoints for mobile and web clients")
public class OAuth2Controller {

    @Autowired
    private GoogleAuthService googleAuthService;

    @Autowired
    private AppleAuthService appleAuthService;

    @Autowired
    private AppleEmailVerificationService appleEmailVerificationService;

    /**
     * Mobile Google Sign-In endpoint.
     * 
     * This endpoint is designed for mobile apps (React Native, iOS, Android) that use
     * Google Sign-In SDK. The mobile app gets a Google ID token from the SDK and sends
     * it here for verification and FixHomi JWT token issuance.
     * 
     * Flow:
     * 1. Mobile app shows Google Sign-In button
     * 2. User taps and authenticates with Google
     * 3. Mobile app receives Google ID token
     * 4. Mobile app POST this token to this endpoint
     * 5. Backend verifies token with Google
     * 6. Backend returns FixHomi JWT access + refresh tokens
     * 
     * POST /api/auth/oauth2/google/mobile
     *
     * @param request Google ID token from mobile SDK
     * @return JWT tokens and user info
     */
    @Operation(
        summary = "Mobile Google Sign-In",
        description = "Authenticate using Google ID token from mobile SDK (React Native, iOS, Android). " +
                      "The mobile app should use @react-native-google-signin/google-signin or similar SDK " +
                      "to get the ID token, then send it to this endpoint."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Authentication successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired Google token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Account is deactivated"
        )
    })
    @PostMapping("/google/mobile")
    public ResponseEntity<LoginResponse> googleMobileAuth(
            @Valid @RequestBody GoogleMobileAuthRequest request) {

        LoginResponse response = googleAuthService.authenticateWithGoogle(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Mobile Apple Sign-In endpoint.
     *
     * This endpoint is designed for iOS apps that use Apple Sign-In.
     * The mobile app gets an Apple identity token (RS256 JWT) and sends
     * it here for verification and FixHomi JWT token issuance.
     *
     * Flow:
     * 1. Mobile app shows Apple Sign-In button (iOS only)
     * 2. User authenticates with Face ID / Touch ID / password
     * 3. Mobile app receives Apple identity token + authorization code
     * 4. Mobile app POST this token to this endpoint
     * 5. Backend verifies token with Apple's public keys (JWKS)
     * 6. Backend returns FixHomi JWT access + refresh tokens
     *
     * POST /api/auth/oauth2/apple/mobile
     *
     * @param request Apple identity token from mobile SDK
     * @return JWT tokens and user info
     */
    @Operation(
        summary = "Mobile Apple Sign-In",
        description = "Authenticate using Apple identity token from iOS SDK " +
                      "(@invertase/react-native-apple-authentication). " +
                      "The identity token is verified using Apple's JWKS public keys."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Authentication successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Invalid or expired Apple token"
        ),
        @ApiResponse(
            responseCode = "403",
            description = "Account is deactivated"
        )
    })
    @PostMapping("/apple/mobile")
    public ResponseEntity<LoginResponse> appleMobileAuth(
            @Valid @RequestBody AppleMobileAuthRequest request) {

        LoginResponse response = appleAuthService.authenticateWithApple(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Send email OTP for Apple Sign-In email verification.
     * Used when Apple does not provide the user's email and the user
     * must verify their email before registration can proceed.
     *
     * POST /api/auth/oauth2/apple/send-email-otp
     *
     * @param body must contain "email" and "appleUserId"
     * @return success message
     */
    @Operation(
        summary = "Send Apple email verification OTP",
        description = "Send a 6-digit OTP to the provided email for Apple Sign-In email verification. " +
                      "Rate limited to 3 requests per minute per Apple user."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP sent successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PostMapping("/apple/send-email-otp")
    public ResponseEntity<MessageResponse> appleSendEmailOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String appleUserId = body.get("appleUserId");

        // Input validation
        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("A valid email address is required."));
        }
        if (appleUserId == null || appleUserId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Apple user ID is required."));
        }

        appleEmailVerificationService.sendOtp(email, appleUserId);

        return ResponseEntity.ok(new MessageResponse("Verification code sent to " + email));
    }

    /**
     * Verify email OTP for Apple Sign-In email verification.
     * Returns a signed verification token that can be included in the
     * Apple mobile auth request to prove the email was verified.
     *
     * POST /api/auth/oauth2/apple/verify-email-otp
     *
     * @param body must contain "email", "appleUserId", and "otp"
     * @return verification result with signed token
     */
    @Operation(
        summary = "Verify Apple email OTP",
        description = "Verify the 6-digit OTP sent to the user's email. " +
                      "Returns a signed verification token (10 min TTL) to use with Apple mobile auth."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OTP verified successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
    })
    @PostMapping("/apple/verify-email-otp")
    public ResponseEntity<Map<String, Object>> appleVerifyEmailOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String appleUserId = body.get("appleUserId");
        String otp = body.get("otp");

        // Input validation
        if (email == null || email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("verified", false, "error", "A valid email address is required."));
        }
        if (appleUserId == null || appleUserId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("verified", false, "error", "Apple user ID is required."));
        }
        if (otp == null || !otp.matches("^\\d{6}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("verified", false, "error", "A valid 6-digit verification code is required."));
        }

        String verificationToken = appleEmailVerificationService.verifyOtp(email, appleUserId, otp);

        return ResponseEntity.ok(Map.of(
                "verified", true,
                "verificationToken", verificationToken
        ));
    }
}
