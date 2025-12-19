package com.fixhomi.auth.controller;

import com.fixhomi.auth.dto.TokenValidationResponse;
import com.fixhomi.auth.service.TokenValidationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for token validation endpoints.
 * Used by Node.js services to validate JWTs issued by this auth service.
 */
@RestController
@RequestMapping("/api/token")
public class TokenController {

    @Autowired
    private TokenValidationService tokenValidationService;

    /**
     * Validate JWT token and return claims.
     * This endpoint is protected - requires valid JWT to access.
     * 
     * GET /api/token/validate
     * Authorization: Bearer <token>
     *
     * Use case: Node.js services call this to verify a user's token
     * and get their identity/role without parsing JWT themselves.
     *
     * @param request HTTP request containing Authorization header
     * @return token claims if valid
     */
    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validateToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        TokenValidationResponse response = tokenValidationService.validateToken(authHeader);
        return ResponseEntity.ok(response);
    }

    /**
     * Introspect current authenticated user's token.
     * Returns the claims from the JWT used to authenticate this request.
     * 
     * GET /api/token/me
     * Authorization: Bearer <token>
     *
     * Use case: Frontend or Node.js services want to know the current
     * user's identity from their token.
     *
     * @param request HTTP request containing Authorization header
     * @return token claims for current user
     */
    @GetMapping("/me")
    public ResponseEntity<TokenValidationResponse> getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        TokenValidationResponse response = tokenValidationService.validateToken(authHeader);
        return ResponseEntity.ok(response);
    }
}
