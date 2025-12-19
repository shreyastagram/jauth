package com.fixhomi.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 * 
 * Access the documentation at:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 * - OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${fixhomi.api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    @Bean
    public OpenAPI fixhomiOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server()
                                .url(apiBaseUrl)
                                .description("FixHomi Auth Service")
                ))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", 
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT access token. Get it from /api/auth/login or /api/auth/register")
                        )
                );
    }

    private Info apiInfo() {
        return new Info()
                .title("FixHomi Authentication Service API")
                .description("""
                        ## Overview
                        FixHomi Authentication Service provides secure user authentication and authorization
                        for the FixHomi home services platform.
                        
                        ## Features
                        - **Email/Password Authentication** - Traditional login and registration
                        - **Passwordless OTP Login** - Phone and Email OTP-based authentication 🆕
                        - **Google OAuth2** - Web-based Google Sign-In
                        - **Mobile Google Sign-In** - Token-based Google authentication for React Native/mobile apps
                        - **JWT Tokens** - Stateless authentication with access and refresh tokens
                        - **Phone Verification** - OTP-based phone number verification (Twilio)
                        - **Email Verification** - Email verification with secure tokens
                        - **Password Reset** - Secure password recovery flow
                        - **Role-based Access Control** - USER, SERVICE_PROVIDER, ADMIN, IT_ADMIN roles
                        
                        ## Authentication Flow
                        1. Register or Login to get access token and refresh token
                        2. Include access token in Authorization header: `Bearer <token>`
                        3. When access token expires, use refresh token to get new tokens
                        4. Refresh tokens are rotated on each use for security
                        
                        ## Passwordless OTP Login (NEW)
                        - **Phone OTP**: POST `/api/auth/login/phone/send-otp` → POST `/api/auth/login/phone/verify`
                        - **Email OTP**: POST `/api/auth/login/email/send-otp` → POST `/api/auth/login/email/verify`
                        
                        ## Rate Limiting
                        - Login/Register: 10 requests/minute
                        - OTP/Verification: 5 requests/minute  
                        - General API: 100 requests/minute
                        
                        ## Mobile Integration
                        For React Native apps, use the `/api/auth/oauth2/google/mobile` endpoint
                        with Google Sign-In SDK to authenticate users.
                        """)
                .version("1.1.0")
                .contact(new Contact()
                        .name("FixHomi Team")
                        .email("support@fixhomi.com")
                        .url("https://fixhomi.com")
                )
                .license(new License()
                        .name("Proprietary")
                        .url("https://fixhomi.com/terms")
                );
    }
}
