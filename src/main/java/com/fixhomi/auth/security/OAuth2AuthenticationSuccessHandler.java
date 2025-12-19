package com.fixhomi.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixhomi.auth.dto.LoginResponse;
import com.fixhomi.auth.entity.RefreshToken;
import com.fixhomi.auth.entity.Role;
import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.repository.UserRepository;
import com.fixhomi.auth.service.RefreshTokenService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Handler for successful OAuth2 authentication.
 * Issues FixHomi JWT access token and refresh token after Google login.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        
        if (email == null || email.isBlank()) {
            logger.error("OAuth2 login failed: email not provided by Google");
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                    "Email not provided by Google. Please ensure email scope is granted.");
            return;
        }

        logger.debug("OAuth2 login attempt for email: {}", email);

        // Find or create user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> createOAuthUser(email, name));

        // Check if user is active
        if (!user.getIsActive()) {
            logger.warn("OAuth2 login blocked for disabled user: {}", email);
            sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, 
                    "Account is deactivated. Please contact support.");
            return;
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

        // Build response
        LoginResponse loginResponse = new LoginResponse(
                accessToken,
                refreshToken.getToken(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                jwtService.getExpirationTimeInSeconds()
        );

        logger.info("OAuth2 login successful for user: {} (role: {})", user.getEmail(), user.getRole());

        // Send JSON response
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
    }

    /**
     * Create a new user from OAuth2 data.
     * Auto-registered users get role=USER and emailVerified=true.
     */
    private User createOAuthUser(String email, String name) {
        logger.info("Auto-registering new OAuth2 user: {}", email);

        User user = new User();
        user.setEmail(email);
        user.setFullName(name != null ? name : email.split("@")[0]);
        user.setPasswordHash(null); // OAuth users don't have password
        user.setRole(Role.USER);
        user.setIsActive(true);
        user.setIsEmailVerified(true); // Google has verified the email
        user.setIsPhoneVerified(false);

        return userRepository.save(user);
    }

    /**
     * Send error response as JSON.
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);
        response.getWriter().write(String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"status\":%d}",
                status == HttpServletResponse.SC_FORBIDDEN ? "Forbidden" : "Bad Request",
                message,
                status
        ));
    }
}
