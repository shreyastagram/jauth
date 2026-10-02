# 09 · Configuration Reference

Configuration lives in three YAML files plus environment variables. Spring Boot loads `application.yaml`, then `application-<profile>.yaml` on top of it. Environment variables override both, either through the `${VAR:default}` placeholders below or through relaxed binding (`fixhomi.rate-limit.enabled` ← `FIXHOMI_RATE_LIMIT_ENABLED`).

Values are read in code with `@Value("${...}")`. `JwtProperties` and `FixhomiProperties` exist as `@ConfigurationProperties` classes, but no code reads them, so their Java defaults (e.g. a 7-day refresh token) have no effect.

## 1. Environment variables

| Variable | Property | Default (base yaml) | Needed locally? | Notes |
|---|---|---|---|---|
| `JWT_SECRET` | `jwt.secret` | **none** | **yes** | HS512 key, ≥ 64 bytes. Must equal Node's `JWT_SECRET` in every deployed pair. `run.sh` refuses to start with a shorter one |
| `SPRING_PROFILES_ACTIVE` | — | (none) | yes: `local` | `run.sh` falls back to `prod` if unset |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | `spring.security.oauth2.client.registration.google.*` | `""` (startup fails if empty) | no (the `local` profile supplies a placeholder) | Web OAuth client; also an accepted audience for mobile ID tokens |
| `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID` | `fixhomi.oauth.google.*-client-id` | `""` | no | Extra audiences for mobile Google ID tokens |
| `APPLE_BUNDLE_ID`, `APPLE_SERVICE_ID` | `fixhomi.oauth.apple.*` | `""` | no | Apple Sign-In audience; empty → Apple returns 401 "not configured" |
| `SMS_PROVIDER` | `fixhomi.notification.sms.provider` | `stub` | no (`local` forces stub) | `msg91` in prod |
| `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID`, `MSG91_VERIFICATION_TEMPLATE_ID`, `MSG91_DELETE_TEMPLATE_ID`, `MSG91_SENDER_ID` | `fixhomi.notification.sms.msg91.*` | `""` | no | Login/signup/reset template; phone-verify template; delete-account template |
| `EMAIL_PROVIDER` | `fixhomi.notification.email.provider` | `stub` | no (`local` forces stub) | `brevo` in prod |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | `fixhomi.notification.email.brevo.*` | `""`, `noreply@fixhomi.com`, `FixHomi` | no | |
| `FIXHOMI_BASE_URL` | `fixhomi.verification.email.base-url` and `fixhomi.api.base-url` | `https://auth.fixhomi.com` / `http://localhost:8080` | no | Prefix of the email-verification link |
| `FIXHOMI_FRONTEND_URL` | `fixhomi.verification.password-reset.base-url` | `https://auth.fixhomi.com` | no | Prefix of the password-reset link |
| `FIXHOMI_DEEP_LINK_SCHEME` | `fixhomi.app.deep-link-scheme` | `fixhomi` | no | Used in the email-verified HTML page redirect |
| `ALLOWED_ORIGINS` | `fixhomi.cors.allowed-origins` | `http://localhost:3000,http://localhost:5001,http://localhost:8081` | no | Comma-separated; `*` is rejected at startup |
| `RATE_LIMIT_ENABLED` | `fixhomi.rate-limit.enabled` | `true` | optional | `false` for Postman Collection Runner |
| `NODEJS_BACKEND_URL` | `fixhomi.backend.nodejs-url` | `http://localhost:5001` | no | Bound but not read by any code |
| `LOCAL_ADMIN_EMAIL`, `LOCAL_ADMIN_PASSWORD` | `fixhomi.local.seed-admin.*` | `admin@fixhomi.local` / `LocalAdmin@123` | optional | `local` profile only |
| `DATABASE_HOST`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | `spring.datasource.*` in `application-prod.yaml` | none | no | Neon PostgreSQL, `sslmode=require` |
| `PORT` | `server.port` (prod only) | `8080` | no | Render injects it |

Watch for `FIXHOMI_NOTIFICATION_SMS_PROVIDER` / `FIXHOMI_NOTIFICATION_EMAIL_PROVIDER` in your shell. Through relaxed binding they override even the `local` profile's `stub` setting.

## 2. Fixed values in YAML (not env-driven)

| Property | Value | Meaning |
|---|---|---|
| `jwt.expiration.ms` | `86400000` | access token lifetime: 24 h (`expiresIn: 86400`) |
| `jwt.issuer` | `fixhomi-auth-service` | `iss` claim |
| `jwt.refresh-token.expiration.days` | `60` | refresh token lifetime |
| `jwt.refresh-token.rotation-grace-seconds` | `45` | replay window after rotation |
| `fixhomi.verification.otp.length` | `6` | |
| `fixhomi.verification.otp.expiration-minutes` | `5` | |
| `fixhomi.verification.otp.max-attempts` | `3` | per OTP |
| `fixhomi.verification.otp.rate-limit-minutes` / `-max-requests` | `5` / `3` | sends per phone/email |
| `fixhomi.verification.email.expiration-hours` | `24` | email-verification link |
| `fixhomi.verification.email.rate-limit-minutes` | `5` | resend cooldown |
| `fixhomi.verification.password-reset.expiration-hours` | `1` | reset link |
| `fixhomi.rate-limit.{auth,otp,general}.requests-per-minute` | `10` / `5` / `100` | per-IP buckets |
| `server.port` | `8080` | |
| `spring.jpa.hibernate.ddl-auto` | `update` | all profiles |
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | `/actuator/*` |

Values only defaulted in code (no YAML entry):
- `fixhomi.auth.lockout.max-attempts` = 5, `fixhomi.auth.lockout.duration-minutes` = 15 (`AuthService`)
- `fixhomi.verification.delete-otp.*` = 5 minutes / 3 attempts (`UserService`)
- `fixhomi.verification.password-reset.web-fallback-url` = `https://fixhomi.com/auth`

## 3. What each profile changes

| Key | base `application.yaml` | `application-local.yaml` | `application-prod.yaml` |
|---|---|---|---|
| datasource | `jdbc:h2:mem:fixhomi_auth` | `jdbc:h2:file:./.localdb/fixhomi_auth;AUTO_SERVER=TRUE` | `jdbc:postgresql://${DATABASE_HOST}/${DATABASE_NAME}?sslmode=require`, Hikari pool 25 |
| `show-sql` | true | false | false |
| H2 console | enabled (but blocked by security) | inherited | disabled |
| Google client id | `${GOOGLE_CLIENT_ID:}` | `${GOOGLE_CLIENT_ID:local-google-oauth-not-configured}` | `${GOOGLE_CLIENT_ID}` (required) |
| SMS / email provider | `${SMS_PROVIDER:stub}` / `${EMAIL_PROVIDER:stub}` | `stub` / `stub` | from env (`msg91` / `brevo` per `render.yaml`) |
| Admin seeding | — | `fixhomi.local.seed-admin.enabled: true` | — |
| Swagger | on (needs a token) | inherited | off |
| Logging | Security DEBUG, SQL TRACE | Security INFO, SQL INFO | INFO/WARN |
| CORS default | localhost origins | inherited | `https://app.fixhomi.com,https://api.fixhomi.com` |

`src/test/resources/application-test.yaml` (profile `test`) gives the test suite an in-memory H2, a fixed test-only JWT secret, stub providers and rate limiting off.

## 4. Files that carry configuration

| File | Purpose | Commit? |
|---|---|---|
| `.env` | your local secrets, loaded by `run.sh` | **never** (git-ignored) |
| `.env.local.example` | template for local `.env` | yes |
| `.env.example` | template for the production env (reference) | yes |
| `render.yaml` | Render blueprint: env var names, `SPRING_PROFILES_ACTIVE=prod`, health check | yes, but secrets are set in the Render dashboard |
| `src/main/resources/*.yaml` | configuration above | yes |

Next: [10-behaviours-and-gotchas.md](10-behaviours-and-gotchas.md)
