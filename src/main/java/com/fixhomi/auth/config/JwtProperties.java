package com.fixhomi.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT settings.
 * Binds to 'jwt.*' properties in application.yaml.
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private String issuer;
    private Expiration expiration = new Expiration();
    private RefreshToken refreshToken = new RefreshToken();

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Expiration getExpiration() {
        return expiration;
    }

    public void setExpiration(Expiration expiration) {
        this.expiration = expiration;
    }

    public RefreshToken getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(RefreshToken refreshToken) {
        this.refreshToken = refreshToken;
    }

    public static class Expiration {
        private long ms = 86400000; // 24 hours default

        public long getMs() {
            return ms;
        }

        public void setMs(long ms) {
            this.ms = ms;
        }
    }

    public static class RefreshToken {
        private Expiration expiration = new Expiration();

        public Expiration getExpiration() {
            return expiration;
        }

        public void setExpiration(Expiration expiration) {
            this.expiration = expiration;
        }

        public static class Expiration {
            private int days = 7; // 7 days default

            public int getDays() {
                return days;
            }

            public void setDays(int days) {
                this.days = days;
            }
        }
    }
}
