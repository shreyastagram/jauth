package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.GoogleMobileAuthRequest;
import com.fixhomi.auth.dto.LoginResponse;
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
}
