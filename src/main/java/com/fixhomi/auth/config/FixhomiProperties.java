package com.fixhomi.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for FixHomi application settings.
 * Binds to 'fixhomi.*' properties in application.yaml.
 */
@Configuration
@ConfigurationProperties(prefix = "fixhomi")
public class FixhomiProperties {

    private Verification verification = new Verification();
    private Notification notification = new Notification();

    public Verification getVerification() {
        return verification;
    }

    public void setVerification(Verification verification) {
        this.verification = verification;
    }

    public Notification getNotification() {
        return notification;
    }

    public void setNotification(Notification notification) {
        this.notification = notification;
    }

    /**
     * Verification related settings.
     */
    public static class Verification {
        private Otp otp = new Otp();
        private Email email = new Email();
        private PasswordReset passwordReset = new PasswordReset();

        public Otp getOtp() {
            return otp;
        }

        public void setOtp(Otp otp) {
            this.otp = otp;
        }

        public Email getEmail() {
            return email;
        }

        public void setEmail(Email email) {
            this.email = email;
        }

        public PasswordReset getPasswordReset() {
            return passwordReset;
        }

        public void setPasswordReset(PasswordReset passwordReset) {
            this.passwordReset = passwordReset;
        }

        /**
         * OTP (Phone verification) settings.
         */
        public static class Otp {
            private int expirationMinutes = 5;
            private int maxAttempts = 3;
            private int length = 6;
            private int rateLimitMinutes = 1;
            private int rateLimitMaxRequests = 3;

            public int getExpirationMinutes() {
                return expirationMinutes;
            }

            public void setExpirationMinutes(int expirationMinutes) {
                this.expirationMinutes = expirationMinutes;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public int getLength() {
                return length;
            }

            public void setLength(int length) {
                this.length = length;
            }

            public int getRateLimitMinutes() {
                return rateLimitMinutes;
            }

            public void setRateLimitMinutes(int rateLimitMinutes) {
                this.rateLimitMinutes = rateLimitMinutes;
            }

            public int getRateLimitMaxRequests() {
                return rateLimitMaxRequests;
            }

            public void setRateLimitMaxRequests(int rateLimitMaxRequests) {
                this.rateLimitMaxRequests = rateLimitMaxRequests;
            }
        }

        /**
         * Email verification settings.
         */
        public static class Email {
            private int expirationHours = 24;
            private int rateLimitMinutes = 5;
            private String baseUrl = "http://localhost:8080";

            public int getExpirationHours() {
                return expirationHours;
            }

            public void setExpirationHours(int expirationHours) {
                this.expirationHours = expirationHours;
            }

            public int getRateLimitMinutes() {
                return rateLimitMinutes;
            }

            public void setRateLimitMinutes(int rateLimitMinutes) {
                this.rateLimitMinutes = rateLimitMinutes;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }
        }

        /**
         * Password reset settings.
         */
        public static class PasswordReset {
            private int expirationHours = 1;
            private int rateLimitMinutes = 5;
            private String baseUrl = "http://localhost:3000";

            public int getExpirationHours() {
                return expirationHours;
            }

            public void setExpirationHours(int expirationHours) {
                this.expirationHours = expirationHours;
            }

            public int getRateLimitMinutes() {
                return rateLimitMinutes;
            }

            public void setRateLimitMinutes(int rateLimitMinutes) {
                this.rateLimitMinutes = rateLimitMinutes;
            }

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }
        }
    }

    /**
     * Notification services configuration.
     */
    public static class Notification {
        private Sms sms = new Sms();
        private EmailConfig email = new EmailConfig();

        public Sms getSms() {
            return sms;
        }

        public void setSms(Sms sms) {
            this.sms = sms;
        }

        public EmailConfig getEmail() {
            return email;
        }

        public void setEmail(EmailConfig email) {
            this.email = email;
        }

        /**
         * SMS notification settings (Twilio).
         */
        public static class Sms {
            private String provider = "stub";
            private Twilio twilio = new Twilio();

            public String getProvider() {
                return provider;
            }

            public void setProvider(String provider) {
                this.provider = provider;
            }

            public Twilio getTwilio() {
                return twilio;
            }

            public void setTwilio(Twilio twilio) {
                this.twilio = twilio;
            }

            public static class Twilio {
                private String accountSid;
                private String authToken;
                private String fromNumber;

                public String getAccountSid() {
                    return accountSid;
                }

                public void setAccountSid(String accountSid) {
                    this.accountSid = accountSid;
                }

                public String getAuthToken() {
                    return authToken;
                }

                public void setAuthToken(String authToken) {
                    this.authToken = authToken;
                }

                public String getFromNumber() {
                    return fromNumber;
                }

                public void setFromNumber(String fromNumber) {
                    this.fromNumber = fromNumber;
                }
            }
        }

        /**
         * Email notification settings (Brevo).
         */
        public static class EmailConfig {
            private String provider = "stub";
            private Brevo brevo = new Brevo();

            public String getProvider() {
                return provider;
            }

            public void setProvider(String provider) {
                this.provider = provider;
            }

            public Brevo getBrevo() {
                return brevo;
            }

            public void setBrevo(Brevo brevo) {
                this.brevo = brevo;
            }

            public static class Brevo {
                private String apiKey;
                private String senderEmail = "noreply@fixhomi.com";
                private String senderName = "FixHomi";

                public String getApiKey() {
                    return apiKey;
                }

                public void setApiKey(String apiKey) {
                    this.apiKey = apiKey;
                }

                public String getSenderEmail() {
                    return senderEmail;
                }

                public void setSenderEmail(String senderEmail) {
                    this.senderEmail = senderEmail;
                }

                public String getSenderName() {
                    return senderName;
                }

                public void setSenderName(String senderName) {
                    this.senderName = senderName;
                }
            }
        }
    }
}
