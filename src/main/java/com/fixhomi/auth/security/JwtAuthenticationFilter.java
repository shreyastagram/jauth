package com.fixhomi.auth.security;

import com.fixhomi.auth.entity.User;
import com.fixhomi.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * JWT authentication filter that validates JWT tokens from Authorization header.
 * Executes once per request before Spring Security filter chain.
 * 
 * Security: Also verifies the user account is still active in the database,
 * ensuring disabled users cannot access APIs even with valid JWTs.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull HttpServletRequest request,
                                    @org.springframework.lang.NonNull HttpServletResponse response,
                                    @org.springframework.lang.NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (jwt != null && jwtService.validateToken(jwt)) {
                // Extract user details from JWT
                String email = jwtService.getEmailFromToken(jwt);
                
                // SECURITY: Verify user is still active in database
                // This ensures disabled users cannot use previously issued JWTs
                Optional<User> userOpt = userRepository.findByEmail(email);
                
                if (userOpt.isEmpty()) {
                    logger.warn("JWT valid but user not found in database: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                User user = userOpt.get();
                if (!user.getIsActive()) {
                    logger.warn("JWT valid but user account is disabled: {}", email);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                String role = jwtService.getRoleFromToken(jwt).name();

                // Create authentication token
                // Using email as principal, userId can be retrieved from JWT when needed
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                Collections.singletonList(authority)
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                logger.debug("Set authentication for user: {} with role: {}", email, role);
            }
        } catch (Exception ex) {
            logger.error("Cannot set user authentication: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header.
     *
     * @param request HTTP request
     * @return JWT token or null if not present
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
