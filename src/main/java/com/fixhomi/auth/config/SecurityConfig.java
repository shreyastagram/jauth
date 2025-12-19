package com.fixhomi.auth.config;

import com.fixhomi.auth.security.JwtAuthenticationFilter;
import com.fixhomi.auth.security.OAuth2AuthenticationFailureHandler;
import com.fixhomi.auth.security.OAuth2AuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for JWT-based authentication with OAuth2 support.
 * Uses modern SecurityFilterChain approach (Spring Security 6.x compatible).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    /**
     * Configure HTTP security with JWT authentication.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for stateless JWT authentication
                .csrf(csrf -> csrf.disable())
                
                // Configure CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                
                // Stateless session management
                .sessionManagement(session -> 
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                    // Public endpoints (no authentication required)
                    .requestMatchers(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/health",
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
                        "/api/auth/reset-password/validate",
                        "/api/auth/email/verify",
                        "/oauth2/**",
                        "/login/oauth2/**",
                        "/actuator/health",
                        "/h2-console/**"
                    ).permitAll()
                    
                    // Admin endpoints - protected by @PreAuthorize at method level
                    .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "IT_ADMIN")
                    
                    // All other requests require authentication
                    .anyRequest().authenticated()
                )
                
                // Configure OAuth2 login
                .oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(authorization -> authorization
                        .baseUri("/oauth2/authorize")
                    )
                    .redirectionEndpoint(redirection -> redirection
                        .baseUri("/oauth2/callback/*")
                    )
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler)
                )
                
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder using BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // strength = 12
    }

    /**
     * Authentication manager for handling authentication requests.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS configuration to allow Node.js services and frontend to access this service.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow specific origins (configure based on your environment)
        configuration.setAllowedOrigins(List.of(
            "http://localhost:3000",    // Node.js service
            "http://localhost:4200",    // Angular frontend
            "http://localhost:5173"     // Vite/React frontend
        ));
        
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
