# 🔐 Environment Variables Reference

This document lists ALL environment variables required by the FixHomi Auth Service, cross-referenced with your application configuration files.

## ✅ Complete Environment Variables Checklist

### 🔵 REQUIRED - Core Application (Must Set)

| Variable | Source | Default | Description |
|----------|--------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | [application.yaml](src/main/resources/application.yaml#L5) | `prod` | Active Spring profile |
| `PORT` | [application-prod.yaml](src/main/resources/application-prod.yaml#L77) | `8080` | Server port (Render auto-sets this) |
| `JWT_SECRET` | [application.yaml](src/main/resources/application.yaml#L56) | ⚠️ **REQUIRED** | JWT signing secret (min 32 chars) |
| `JWT_EXPIRATION_MS` | [render.yaml](render.yaml) | `86400000` | JWT token expiry (24h in ms) |
| `JWT_REFRESH_EXPIRATION_MS` | [render.yaml](render.yaml) | `604800000` | Refresh token expiry (7d in ms) |

**Generate JWT_SECRET:**
```bash
openssl rand -base64 64
```

---

### 🔵 REQUIRED - Database (PostgreSQL)

| Variable | Source | Description |
|----------|--------|-------------|
| `DATABASE_HOST` | [application-prod.yaml](src/main/resources/application-prod.yaml#L18) | PostgreSQL hostname |
| `DATABASE_PORT` | [render.yaml](render.yaml) | PostgreSQL port (usually 5432) |
| `DATABASE_NAME` | [application-prod.yaml](src/main/resources/application-prod.yaml#L18) | Database name |
| `DATABASE_USERNAME` | [application-prod.yaml](src/main/resources/application-prod.yaml#L19) | Database username ⚠️ Note: NOT `DATABASE_USER` |
| `DATABASE_PASSWORD` | [application-prod.yaml](src/main/resources/application-prod.yaml#L20) | Database password |

**Note:** Render auto-populates these when you create a PostgreSQL database via `render.yaml`.

---

### 🟡 OPTIONAL - Email Notifications

**Choose your provider:**
- `stub` = Development mode (emails logged, not sent)
- `brevo` = Production email via Brevo (SendinBlue)

| Variable | Source | Default | Description |
|----------|--------|---------|-------------|
| `EMAIL_PROVIDER` | [application.yaml](src/main/resources/application.yaml#L107) | `stub` | Email provider: `stub` or `brevo` |
| `BREVO_API_KEY` | [application.yaml](src/main/resources/application.yaml#L109) | - | Brevo API key (if provider=brevo) |
| `BREVO_SENDER_EMAIL` | [application.yaml](src/main/resources/application.yaml#L110) | `noreply@fixhomi.com` | From email address |
| `BREVO_SENDER_NAME` | [application.yaml](src/main/resources/application.yaml#L111) | `FixHomi` | From sender name |

**Get Brevo API Key:**
1. Sign up at https://www.brevo.com/
2. Go to Settings → API Keys
3. Create new API key

---

### 🟡 OPTIONAL - SMS Notifications (Twilio)

**Choose your provider:**
- `stub` = Development mode (SMS logged, not sent)
- `twilio` = Production SMS via Twilio

| Variable | Source | Default | Description |
|----------|--------|---------|-------------|
| `SMS_PROVIDER` | [application.yaml](src/main/resources/application.yaml#L115) | `stub` | SMS provider: `stub` or `twilio` |
| `TWILIO_ACCOUNT_SID` | [application.yaml](src/main/resources/application.yaml#L117) | - | Twilio Account SID |
| `TWILIO_AUTH_TOKEN` | [application.yaml](src/main/resources/application.yaml#L118) | - | Twilio Auth Token |
| `TWILIO_PHONE_NUMBER` | [application.yaml](src/main/resources/application.yaml#L119) | - | Twilio phone number (e.g., +1234567890) |

**Get Twilio Credentials:**
1. Sign up at https://www.twilio.com/
2. Get free trial credits
3. Copy SID and Token from Console Dashboard

---

### 🟡 OPTIONAL - OAuth2 (Google Sign-In)

#### Web OAuth (for web applications)

| Variable | Source | Description |
|----------|--------|-------------|
| `GOOGLE_CLIENT_ID` | [application-prod.yaml](src/main/resources/application-prod.yaml#L53) | Google OAuth Web Client ID |
| `GOOGLE_CLIENT_SECRET` | [application-prod.yaml](src/main/resources/application-prod.yaml#L54) | Google OAuth Web Client Secret |

#### Mobile OAuth (for React Native apps)

| Variable | Source | Description |
|----------|--------|-------------|
| `GOOGLE_IOS_CLIENT_ID` | [application.yaml](src/main/resources/application.yaml#L143) | Google OAuth iOS Client ID |
| `GOOGLE_ANDROID_CLIENT_ID` | [application.yaml](src/main/resources/application.yaml#L144) | Google OAuth Android Client ID |

**Setup Google OAuth:**
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create project → Enable Google+ API
3. Create OAuth 2.0 credentials
4. Add authorized redirect URI: `https://your-app.onrender.com/oauth2/callback/google`

---

### 🟢 OPTIONAL - Application URLs

| Variable | Source | Default | Description |
|----------|--------|---------|-------------|
| `FIXHOMI_BASE_URL` | [application-prod.yaml](src/main/resources/application-prod.yaml#L108) | `https://api.fixhomi.com` | API base URL for email links |
| `FIXHOMI_FRONTEND_URL` | [application-prod.yaml](src/main/resources/application-prod.yaml#L112) | `https://app.fixhomi.com` | Frontend URL for password reset |

---

### 🟢 OPTIONAL - Rate Limiting

| Variable | Source | Default | Description |
|----------|--------|---------|-------------|
| `RATE_LIMIT_ENABLED` | [application.yaml](src/main/resources/application.yaml#L122) | `true` | Enable/disable rate limiting |

---

## 📋 Quick Setup for Render

### Minimal Setup (Development/Testing)
Set these in Render → Environment:

```env
# Core (auto-generated by Render Blueprint)
JWT_SECRET=<auto-generated>
DATABASE_HOST=<from-postgresql>
DATABASE_USERNAME=<from-postgresql>
DATABASE_PASSWORD=<from-postgresql>

# Providers (use stub for testing)
EMAIL_PROVIDER=stub
SMS_PROVIDER=stub
```

### Production Setup (All Features)
Add these for full functionality:

```env
# Core
JWT_SECRET=<your-generated-secret>

# Database (auto from render.yaml)
DATABASE_HOST=<from-render>
DATABASE_USERNAME=<from-render>
DATABASE_PASSWORD=<from-render>

# Email (Brevo)
EMAIL_PROVIDER=brevo
BREVO_API_KEY=xkeysib-xxx
BREVO_SENDER_EMAIL=noreply@fixhomi.com
BREVO_SENDER_NAME=FixHomi

# SMS (Twilio)
SMS_PROVIDER=twilio
TWILIO_ACCOUNT_SID=ACxxxx
TWILIO_AUTH_TOKEN=xxxx
TWILIO_PHONE_NUMBER=+1234567890

# OAuth (Google)
GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=GOCSPX-xxx
GOOGLE_IOS_CLIENT_ID=xxx.apps.googleusercontent.com
GOOGLE_ANDROID_CLIENT_ID=xxx.apps.googleusercontent.com

# URLs
FIXHOMI_BASE_URL=https://fixhomi-auth-service.onrender.com
FIXHOMI_FRONTEND_URL=https://app.fixhomi.com
```

---

## 🔍 Verification Matrix

Based on your source files:

| Feature | Config File | Environment Variables | Status |
|---------|-------------|----------------------|--------|
| Spring Boot | [pom.xml](pom.xml#L8) | ✅ Java 17, Spring Boot 3.4.12 | ✅ **Correct** |
| Database | [application-prod.yaml](src/main/resources/application-prod.yaml#L14-L29) | `DATABASE_*` (5 vars) | ✅ **Fixed** |
| JWT | [application.yaml](src/main/resources/application.yaml#L54-L60) | `JWT_SECRET`, `JWT_EXPIRATION_MS` | ✅ **Correct** |
| Email | [application.yaml](src/main/resources/application.yaml#L105-L111) | `EMAIL_PROVIDER`, `BREVO_*` | ✅ **Correct** |
| SMS | [application.yaml](src/main/resources/application.yaml#L113-L119) | `SMS_PROVIDER`, `TWILIO_*` | ✅ **Correct** |
| OAuth Web | [application-prod.yaml](src/main/resources/application-prod.yaml#L47-L66) | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | ✅ **Correct** |
| OAuth Mobile | [application.yaml](src/main/resources/application.yaml#L141-L144) | `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID` | ✅ **Added** |
| Actuator | [pom.xml](pom.xml#L107-L110), [application.yaml](src/main/resources/application.yaml#L156-L169) | - | ✅ **Health check ready** |

---

## 🚨 Common Mistakes to Avoid

1. ❌ **Don't use** `DATABASE_USER` → ✅ **Use** `DATABASE_USERNAME` (your app expects this)
2. ❌ **Don't set** `MAIL_HOST` → ✅ **Use** `EMAIL_PROVIDER=brevo` with `BREVO_API_KEY`
3. ❌ **Don't forget** to set `SPRING_PROFILES_ACTIVE=prod` (or app uses H2 memory DB!)
4. ❌ **Don't use** weak JWT_SECRET → ✅ Generate with `openssl rand -base64 64`

---

## 📚 References

- [application.yaml](src/main/resources/application.yaml) - Development config (H2 database)
- [application-prod.yaml](src/main/resources/application-prod.yaml) - Production config (PostgreSQL)
- [pom.xml](pom.xml) - Dependencies (Spring Boot 3.4.12, Java 17)
- [render.yaml](render.yaml) - Render deployment configuration
- [Dockerfile](Dockerfile) - Container build instructions

---

**Last Updated:** Cross-checked with all source files on 2026-01-11
