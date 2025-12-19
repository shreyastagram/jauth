package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.LoginRequest;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.dto.PhoneLoginRequest;
import com.fixhomi.auth.dto.LogoutRequest;
import com.fixhomi.auth.dto.MessageResponse;
import com.fixhomi.auth.dto.RefreshTokenRequest;
import com.fixhomi.auth.dto.RegisterRequest;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.security.JwtService;
import com.fixhomi.auth.service.AuthService;
import com.fixhomi.auth.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Core authentication endpoints for login, registration, and token management")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtService jwtService;

    /**
     * Login endpoint.
     * POST /api/auth/login
     *
     * @param loginRequest login credentials
     * @return JWT token and user info
     */
    @Operation(summary = "User Login", description = "Authenticate with email and password to receive JWT tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "429", description = "Too many login attempts")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        LoginResponse response = authService.login(loginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Phone login endpoint.
     * POST /api/auth/login/phone
     *
     * @param phoneLoginRequest login credentials with phone number
     * @return JWT token and user info
     */
    @Operation(summary = "User Login with Phone", description = "Authenticate with phone number and password to receive JWT tokens")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Login successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials"),
        @ApiResponse(responseCode = "429", description = "Too many login attempts")
    })
    @PostMapping("/login/phone")
    public ResponseEntity<LoginResponse> loginWithPhone(@Valid @RequestBody PhoneLoginRequest phoneLoginRequest) {
        LoginResponse response = authService.loginWithPhone(phoneLoginRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Registration endpoint.
     * POST /api/auth/register
     *
     * @param registerRequest registration details
     * @return JWT token and user info
     */
    @Operation(summary = "User Registration", description = "Register a new user account (USER or SERVICE_PROVIDER roles only)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Registration successful",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input"),
        @ApiResponse(responseCode = "409", description = "Email or phone already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        LoginResponse response = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Refresh access token endpoint.
     * POST /api/auth/refresh
     *
     * Uses refresh token to generate new access token and rotated refresh token.
     *
     * @param request refresh token
     * @return new access token and refresh token
     */
    @Operation(summary = "Refresh Tokens", description = "Exchange refresh token for new access and refresh tokens (rotation)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Tokens refreshed",
            content = @Content(schema = @Schema(implementation = LoginResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        // Rotate refresh token (validates and revokes old, creates new)
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        User user = newRefreshToken.getUser();

        // Generate new access token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                newRefreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds()
        ));
    }

    /**
     * Logout endpoint.
     * POST /api/auth/logout
     *
     * Revokes the provided refresh token.
     *
     * @param request refresh token to revoke
     * @return success message
     */
    @Operation(summary = "Logout", description = "Revoke the refresh token to log out")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Logout successful"),
        @ApiResponse(responseCode = "400", description = "Invalid refresh token")
    })
    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revokeToken(request.getRefreshToken());
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    /**
     * Health check endpoint.
     * GET /api/auth/health
     *
     * @return health status
     */
    @Operation(summary = "Health Check", description = "Check if the auth service is running")
    @ApiResponse(responseCode = "200", description = "Service is healthy")
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Auth service is running"));
    }

    /**
     * Health response DTO.
     */
    public static class HealthResponse {
        private String status;
        private String message;

        public HealthResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
