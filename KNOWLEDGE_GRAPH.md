# FixHomi Auth Service — Complete Knowledge Graph

> **Purpose:** Definitive reference for any AI agent or developer working on this codebase.
> Read this file first before making any changes. Every fact below is verified directly from source code.

---

## 1. PROJECT IDENTITY

| Key | Value |
|-----|-------|
| **Name** | `auth-service` (Maven artifactId) |
| **Package** | `com.fixhomi.auth` |
| **What it does** | Standalone JWT authentication microservice for the FixHomi home-services platform |
| **Framework** | Spring Boot **3.4.12**, Java **17** |
| **Build** | Maven (`pom.xml`) |
| **Entry point** | `AuthServiceApplication.java` |
| **Default port** | `8080` |
| **Deploy target** | Render.com (Docker, `render.yaml`) |

---

## 2. DEPENDENCIES (from pom.xml)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `spring-boot-starter-web` | (managed) | REST API |
| `spring-boot-starter-security` | (managed) | Security framework |
| `spring-boot-starter-data-jpa` | (managed) | ORM / Hibernate |
| `spring-boot-starter-validation` | (managed) | Bean validation (`@Valid`) |
| `spring-boot-starter-oauth2-client` | (managed) | Google OAuth2 web flow |
| `spring-boot-starter-actuator` | (managed) | Health/metrics endpoints |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | **0.11.5** | JWT creation & validation |
| `google-api-client` | **2.2.0** | Google ID token verification (mobile) |
| `bucket4j-core` | **8.7.0** | IP-based rate limiting |
| `springdoc-openapi-starter-webmvc-ui` | **2.8.8** | Swagger UI / OpenAPI docs |
| `h2` | (managed) | Dev in-memory DB |
| `postgresql` | (managed) | Production DB driver |

---

## 3. DATABASE

| Environment | Engine | URL / Connection |
|-------------|--------|-----------------|
| **Development** | H2 in-memory | `jdbc:h2:mem:fixhomi_auth` (user `sa`, no password) |
| **Production** | PostgreSQL | `jdbc:postgresql://${DATABASE_HOST}/${DATABASE_NAME}?sslmode=require` |

- **DDL strategy:** `hibernate.ddl-auto: update` (both environments)
- **Connection pool (prod):** HikariCP — max 5, min idle 2, max lifetime 3 min, keepalive 30s
- **H2 console (dev):** enabled at `/h2-console`
- **JPA auditing:** Enabled (`@EntityListeners(AuditingEntityListener.class)`)

---

## 4. ENTITY DATA MODEL

### 4.1 `User` — table `users`

| Column | Java Type | DB Constraints | Notes |
|--------|-----------|---------------|-------|
| `id` | Long | PK, IDENTITY | auto-generated |
| `email` | String(100) | unique, not null, indexed | auto-lowercased on persist/update |
| `phone_number` | String(20) | unique, indexed | auto-normalized to 10 digits (strips +91/91 prefix, spaces, dashes) |
| `password_hash` | String(60) | nullable | BCrypt. Null for OAuth-only users |
| `full_name` | String(100) | not null | |
| `role` | Enum(STRING, 20) | not null | USER, SERVICE_PROVIDER, ADMIN, SUPPORT, IT_ADMIN |
| `is_active` | Boolean | not null, default true | |
| `is_email_verified` | Boolean | not null, default false | |
| `is_phone_verified` | Boolean | not null, default false | |
| `created_at` | LocalDateTime | not null, immutable | `@CreatedDate` |
| `updated_at` | LocalDateTime | not null | `@LastModifiedDate` |
| `last_login_at` | LocalDateTime | nullable | Updated on each login |

**Phone normalization logic** (`normalizePhoneNumber`):
- `+919356011874` → `9356011874`
- `919356011874` → `9356011874`
- Strings starting with `del_` (soft-deleted placeholders) are left unchanged
- Non-digit characters stripped; if result >10 digits and starts with `91`, strip prefix

### 4.2 `Role` — enum (not a table)

| Value | Description |
|-------|-------------|
| `USER` | Regular customer/end-user |
| `SERVICE_PROVIDER` | Plumber, electrician, etc. |
| `ADMIN` | Full system access |
| `SUPPORT` | Customer support staff |
| `IT_ADMIN` | IT system management |

Public registration allows only `USER` or `SERVICE_PROVIDER`. Admin endpoint allows all roles.

### 4.3 `RefreshToken` — table `refresh_tokens`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `token` | String(255) | unique, indexed | UUID-based opaque token |
| `user_id` | Long | FK → users, indexed | `@ManyToOne(LAZY)` |
| `expires_at` | LocalDateTime | not null | 7 days from creation |
| `revoked` | Boolean | not null, default false | |
| `created_at` | LocalDateTime | not null, immutable | |

Business methods: `isExpired()`, `isValid()` (not revoked AND not expired)

### 4.4 `UserSession` — table `user_sessions`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `user_id` | Long | FK → users, indexed | `@ManyToOne(LAZY)` |
| `refresh_token_id` | Long | FK → refresh_tokens, indexed | `@OneToOne(LAZY)` |
| `device_id` | String(255) | not null, indexed | |
| `device_name` | String(255) | nullable | |
| `device_model` | String(255) | nullable | |
| `platform` | String(50) | nullable | ios, android, web |
| `system_version` | String(50) | nullable | |
| `app_version` | String(50) | nullable | |
| `ip_address` | String(45) | nullable | |
| `location` | String(255) | nullable | |
| `is_trusted` | Boolean | not null, default false | |
| `is_active` | Boolean | not null, default true | |
| `last_activity_at` | LocalDateTime | nullable | |
| `created_at` | LocalDateTime | not null, immutable | |
| `updated_at` | LocalDateTime | nullable | |

Business methods: `isValid()`, `updateLastActivity()`, `revoke()` (sets isActive=false, revokes linked refreshToken)

### 4.5 `TrustedDevice` — table `trusted_devices`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `user_id` | Long | FK → users, indexed | `@ManyToOne(LAZY)` |
| `device_id` | String(255) | not null, indexed | unique constraint: (user_id, device_id) |
| `device_name` | String(255) | nullable | |
| `custom_name` | String(255) | nullable | user-assigned name |
| `device_model` | String(255) | nullable | |
| `platform` | String(50) | nullable | |
| `system_version` | String(50) | nullable | |
| `app_version` | String(50) | nullable | |
| `is_active` | Boolean | not null, default true | |
| `last_used_at` | LocalDateTime | nullable | |
| `trusted_at` | LocalDateTime | not null, immutable | `@CreatedDate` |

### 4.6 `PhoneOtp` — table `phone_otps`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `user_id` | Long | not null, indexed | plain column, NOT a FK |
| `phone_number` | String(20) | not null, indexed | |
| `otp` | String(10) | not null | 6-digit code |
| `expires_at` | LocalDateTime | not null, indexed | 5 minutes |
| `verified` | Boolean | not null, default false | |
| `attempts` | Integer | not null, default 0 | max 3 |
| `created_at` | LocalDateTime | not null, immutable | indexed |

### 4.7 `EmailOtp` — table `email_otps`

Same structure as PhoneOtp but keyed on `email` (String 255) instead of `phone_number`.

### 4.8 `PasswordResetOtp` — table `password_reset_otps`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `user_id` | Long | not null, indexed | |
| `phone_number` | String(20) | nullable, indexed | for phone-based reset |
| `email` | String(255) | nullable, indexed | for email-based reset |
| `otp` | String(10) | not null | |
| `expires_at` | LocalDateTime | not null, indexed | |
| `used` | Boolean | not null, default false | single-use |
| `attempts` | Integer | not null, default 0 | |
| `created_at` | LocalDateTime | not null, immutable | |

Factory method: `PasswordResetOtp.forEmail(userId, email, otp, expiresAt)` creates email-based instance.

### 4.9 `EmailVerificationToken` — table `email_verification_tokens`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `token` | String(64) | unique, indexed | hex token |
| `user_id` | Long | not null, indexed | |
| `email` | String(100) | not null | |
| `expires_at` | LocalDateTime | not null, indexed | 24 hours |
| `verified` | Boolean | not null, default false | |
| `created_at` | LocalDateTime | not null, immutable | |

### 4.10 `PasswordResetToken` — table `password_reset_tokens`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `token` | String(64) | unique, indexed | hex token |
| `user_id` | Long | not null, indexed | |
| `email` | String(100) | not null | |
| `expires_at` | LocalDateTime | not null, indexed | 1 hour |
| `used` | Boolean | not null, default false | single-use |
| `created_at` | LocalDateTime | not null, immutable | |

### 4.11 `DeleteAccountOtp` — table `delete_account_otps`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `user_id` | Long | not null, indexed | |
| `phone_number` | String(20) | not null, indexed | |
| `otp` | String(10) | not null | |
| `expires_at` | LocalDateTime | not null, indexed | |
| `used` | Boolean | not null, default false | |
| `attempts` | Integer | not null, default 0 | |
| `created_at` | LocalDateTime | not null, immutable | |

### 4.12 `LoginLockout` — table `login_lockouts`

| Column | Java Type | Constraints | Notes |
|--------|-----------|-------------|-------|
| `id` | Long | PK | |
| `identifier` | String(255) | not null, indexed | email or phone number |
| `user_id` | Long | nullable, indexed | for cross-method lockout |
| `failed_attempts` | Integer | not null, default 0 | |
| `locked_until` | LocalDateTime | nullable, indexed | null = not locked |
| `last_attempt_at` | LocalDateTime | nullable | |
| `created_at` | LocalDateTime | not null, immutable | |

Business methods:
- `isLocked()` — true if `lockedUntil` is in the future
- `incrementAttempts(maxAttempts, lockoutDurationMinutes)` — increments counter; locks if threshold reached
- `resetAttempts()` — clears counter and lockout

---

## 5. COMPLETE API ENDPOINT INVENTORY

### Legend
- **Auth: No** = public endpoint (listed in SecurityConfig `permitAll`)
- **Auth: JWT** = requires `Authorization: Bearer <token>` header
- **Auth: ADMIN** = requires JWT with role ADMIN or IT_ADMIN (`@PreAuthorize`)

---

### 5.1 AuthController — `/api/auth`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 1 | `POST` | `/api/auth/login` | No | Email + password login |
| 2 | `POST` | `/api/auth/login/phone` | No | Phone + password login |
| 3 | `POST` | `/api/auth/register` | No | Create new user (USER or SERVICE_PROVIDER only) |
| 4 | `POST` | `/api/auth/refresh` | No | Rotate refresh token → new access + refresh tokens |
| 5 | `POST` | `/api/auth/logout` | No | Revoke a refresh token |
| 6 | `GET` | `/api/auth/health` | No | Health check |

**Endpoint 1: POST /api/auth/login**
- Request: `LoginRequest { email: @NotBlank @Email, password: @NotBlank @Size(8-100) }`
- Response 200: `LoginResponse`
- Errors: 401 (invalid credentials), 429 (rate limit / lockout)

**Endpoint 2: POST /api/auth/login/phone**
- Request: `PhoneLoginRequest { phoneNumber: @NotBlank @Pattern("^\\+?[1-9]\\d{6,14}$"), password: @NotBlank @Size(8-100) }`
- Response 200: `LoginResponse`
- Errors: 401, 429

**Endpoint 3: POST /api/auth/register**
- Request: `RegisterRequest { email: @NotBlank @Email, phoneNumber: @Pattern (optional), password: @NotBlank @Size(8-100) @Pattern(uppercase+lowercase+digit+special), fullName: @NotBlank @Size(2-100), role: @NotNull (USER|SERVICE_PROVIDER only) }`
- Response 201: `LoginResponse`
- Errors: 400 (validation), 409 (email or phone already exists)

**Endpoint 4: POST /api/auth/refresh**
- Request: `RefreshTokenRequest { refreshToken: @NotBlank }`
- Response 200: `LoginResponse` (new token pair, old refresh token revoked)
- Errors: 401 (invalid/expired/revoked token, deactivated account)

**Endpoint 5: POST /api/auth/logout**
- Request: `LogoutRequest { refreshToken: @NotBlank }`
- Response 200: `MessageResponse { message }`

**Endpoint 6: GET /api/auth/health**
- Response 200: `{ status: "UP", message: "Auth service is running" }`

---

### 5.2 OtpLoginController — `/api/auth/login`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 7 | `POST` | `/api/auth/login/phone/send-otp` | No | Send OTP to phone for passwordless login |
| 8 | `POST` | `/api/auth/login/phone/verify` | No | Verify phone OTP → login |
| 9 | `POST` | `/api/auth/login/email/send-otp` | No | Send OTP to email for passwordless login |
| 10 | `POST` | `/api/auth/login/email/verify` | No | Verify email OTP → login |

**Endpoint 7: POST /api/auth/login/phone/send-otp**
- Request: `PhoneOtpLoginRequest { phoneNumber: @NotBlank }`
- Response 200: `{ success: true, message, maskedPhone, expiresInMinutes: 5 }`
- Errors: 404 (USER_NOT_FOUND), 429 (TOO_MANY_REQUESTS), 500

**Endpoint 8: POST /api/auth/login/phone/verify**
- Request: `PhoneOtpVerifyRequest { phoneNumber: @NotBlank, otp: @NotBlank }`
- Response 200: `LoginResponse`
- Errors: 400 (INVALID_OTP / OTP_EXPIRED / MAX_ATTEMPTS_EXCEEDED), 404 (USER_NOT_FOUND)

**Endpoint 9: POST /api/auth/login/email/send-otp**
- Request: `EmailOtpLoginRequest { email: @NotBlank @Email }`
- Response 200: `{ success: true, message, maskedEmail, expiresInMinutes: 5 }`
- Errors: 404, 429, 500

**Endpoint 10: POST /api/auth/login/email/verify**
- Request: `EmailOtpVerifyRequest { email: @NotBlank @Email, otp: @NotBlank }`
- Response 200: `LoginResponse`
- Errors: 400, 404

---

### 5.3 OAuth2Controller — `/api/auth/oauth2`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 11 | `POST` | `/api/auth/oauth2/google/mobile` | No | Google Sign-In with ID token from mobile SDK |
| 12 | `POST` | `/api/auth/oauth2/apple/mobile` | No | Apple Sign-In with identity token from iOS |

**Endpoint 11: POST /api/auth/oauth2/google/mobile**
- Request: `GoogleMobileAuthRequest { idToken: @NotBlank, role?: @Pattern("USER|SERVICE_PROVIDER"), mode?: @Pattern("login|signup"), deviceId?, deviceType?, appVersion? }`
- Response 200: `LoginResponse`
- Errors: 401 (invalid token), 403 (deactivated)
- Notes: `mode="login"` → only existing users; `mode="signup"` → only new users; null → auto

**Endpoint 12: POST /api/auth/oauth2/apple/mobile**
- Request: `AppleMobileAuthRequest { identityToken: @NotBlank, authorizationCode?, fullName?, email?, appleUserId?, role?: @Pattern("USER|SERVICE_PROVIDER"), mode?: @Pattern("login|signup"), deviceId?, deviceType?, appVersion? }`
- Response 200: `LoginResponse`
- Errors: 401, 403
- Notes: Apple provides email/name ONLY on first sign-in. Must be saved immediately.

---

### 5.4 VerificationController — `/api/auth`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 13 | `POST` | `/api/auth/otp/send` | JWT | Send OTP to authenticated user's phone |
| 14 | `POST` | `/api/auth/otp/verify` | JWT | Verify phone OTP |
| 15 | `POST` | `/api/auth/email/send-verification` | JWT | Send verification email |
| 16 | `GET` | `/api/auth/email/verify` | No | Verify email via link (returns HTML) |
| 17 | `POST` | `/api/auth/forgot-password` | No | Request email password reset link |
| 18 | `POST` | `/api/auth/reset-password` | No | Reset password with token |
| 19 | `GET` | `/api/auth/reset-password/validate` | No | Validate reset token |
| 20 | `POST` | `/api/auth/forgot-password/phone` | No | Request phone password reset OTP |
| 21 | `POST` | `/api/auth/forgot-password/phone/verify` | No | Verify phone OTP + reset password |
| 22 | `POST` | `/api/auth/forgot-password/email` | No | Request email password reset OTP |
| 23 | `POST` | `/api/auth/forgot-password/email/verify` | No | Verify email OTP + reset password |

**Endpoint 13: POST /api/auth/otp/send**
- Auth: JWT (user email from `Authentication.getName()`)
- Response 200: `VerificationResponse { success, message, maskedContact }`

**Endpoint 14: POST /api/auth/otp/verify**
- Request: `VerifyOtpRequest { otp: @NotBlank }`
- Response 200: `VerificationResponse`

**Endpoint 15: POST /api/auth/email/send-verification**
- Auth: JWT
- Response 200: `VerificationResponse { success, message, maskedContact }`

**Endpoint 16: GET /api/auth/email/verify?token=...**
- Response 200: HTML page with success/error + deep link redirect (`fixhomi://email-verified?...`)

**Endpoint 17: POST /api/auth/forgot-password**
- Request: `ForgotPasswordRequest { email: @NotBlank @Email }`
- Response 200: `VerificationResponse` — **always returns 200** to prevent email enumeration

**Endpoint 18: POST /api/auth/reset-password**
- Request: `ResetPasswordRequest { token: @NotBlank, newPassword: @NotBlank @Size(8-100) @Pattern(strong) }`
- Response 200: `VerificationResponse`

**Endpoint 19: GET /api/auth/reset-password/validate?token=...**
- Response 200: `VerificationResponse { success: true }` or 400: `{ success: false }`

**Endpoint 20: POST /api/auth/forgot-password/phone**
- Request: `ForgotPasswordPhoneRequest { phoneNumber: @NotBlank @Pattern("^[+]?[0-9]{10,15}$") }`
- Response 200: `{ success, message, maskedPhone, expiresInMinutes: 5 }`
- Errors: 404 (USER_NOT_FOUND), 429

**Endpoint 21: POST /api/auth/forgot-password/phone/verify**
- Request: `VerifyOtpAndResetPasswordRequest { phoneNumber: @NotBlank, otp: @NotBlank @Pattern("^[0-9]{6}$"), newPassword: @NotBlank @Size(8-128) @Pattern(strong) }`
- Response 200: `{ success, message }`
- Errors: 400 (INVALID_OTP / OTP_EXPIRED / MAX_ATTEMPTS_EXCEEDED)

**Endpoint 22: POST /api/auth/forgot-password/email**
- Request: `ForgotPasswordRequest { email }`
- Response 200: `VerificationResponse` — always 200 (anti-enumeration)

**Endpoint 23: POST /api/auth/forgot-password/email/verify**
- Request: `VerifyEmailOtpAndResetPasswordRequest { email: @NotBlank @Email, otp: @NotBlank @Pattern("^[0-9]{6}$"), newPassword: @NotBlank @Size(8-128) @Pattern(strong) }`
- Response 200: `{ success, message }`
- Errors: 400

---

### 5.5 SessionController — `/api/auth`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 24 | `GET` | `/api/auth/sessions` | JWT | List all active sessions |
| 25 | `DELETE` | `/api/auth/sessions/{sessionId}` | JWT | Revoke specific session |
| 26 | `POST` | `/api/auth/sessions/revoke-all` | JWT | Revoke all other sessions |
| 27 | `GET` | `/api/auth/validate` | JWT | Validate current access token |
| 28 | `POST` | `/api/auth/devices/trust` | JWT | Trust a device |
| 29 | `GET` | `/api/auth/devices/trust` | JWT | List trusted devices |
| 30 | `DELETE` | `/api/auth/devices/trust/{deviceId}` | JWT | Remove device trust |

**Endpoint 24: GET /api/auth/sessions**
- Header: `X-Device-Id` (optional, marks current device)
- Response 200: `{ sessions: [SessionResponse], count }`

**Endpoint 25: DELETE /api/auth/sessions/{sessionId}**
- Response 200: `{ success, message }`

**Endpoint 26: POST /api/auth/sessions/revoke-all**
- Request (optional): `RevokeAllSessionsRequest { exceptDeviceId? }`
- Response 200: `{ success, message, revokedCount }`

**Endpoint 27: GET /api/auth/validate**
- Response 200: `{ valid: true, userId, email, role, isEmailVerified, isPhoneVerified }`

**Endpoint 28: POST /api/auth/devices/trust**
- Request: `DeviceInfoRequest { deviceId: @NotBlank, deviceName?, deviceModel?, platform?, systemVersion?, appVersion?, buildNumber?, customName?, lastActive? }`
- Response 200: `{ success, message, deviceId, trustedAt }`

**Endpoint 29: GET /api/auth/devices/trust**
- Response 200: `{ devices: [{id, deviceId, deviceName, customName, deviceModel, platform, systemVersion, appVersion, trustedAt, lastUsedAt}], count }`

**Endpoint 30: DELETE /api/auth/devices/trust/{deviceId}**
- Response 200: `{ success, message }`

---

### 5.6 UserController — `/api/users`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 31 | `GET` | `/api/users/me` | JWT | Get current user's profile |
| 32 | `PUT` | `/api/users/profile` | JWT | Update profile (name, phone) |
| 33 | `POST` | `/api/users/change-password` | JWT | Change password |
| 34 | `POST` | `/api/users/delete-account/request-otp` | JWT | Request account deletion OTP |
| 35 | `DELETE` | `/api/users/account` | JWT | Delete account with OTP |
| 36 | `DELETE` | `/api/users/{userId}` | JWT | Delete account by ID (self or admin) |

**Endpoint 31: GET /api/users/me**
- Response 200: `UserProfileResponse`

**Endpoint 32: PUT /api/users/profile**
- Request: `UpdateProfileRequest { fullName?: @Size(2-100), phoneNumber?: @Size(max 20) }`
- Response 200: `UserProfileResponse`

**Endpoint 33: POST /api/users/change-password**
- Request: `ChangePasswordRequest { currentPassword? (nullable for OAuth-only users), newPassword: @NotBlank @Size(8-100) @Pattern(strong) }`
- Response 200: `MessageResponse`

**Endpoint 34: POST /api/users/delete-account/request-otp**
- Response 200: `MessageResponse` with masked phone

**Endpoint 35: DELETE /api/users/account**
- Request: `DeleteAccountRequest { otp: @NotBlank @Pattern("\\d{6}"), reason? }`
- Response 200: `MessageResponse`

**Endpoint 36: DELETE /api/users/{userId}**
- Validates: authenticated user owns the account OR has ADMIN role (IDOR prevention)
- Response 200: `MessageResponse`
- Errors: 401, 403

---

### 5.7 TokenController — `/api/token`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 37 | `GET` | `/api/token/validate` | JWT | Validate token and return claims |
| 38 | `GET` | `/api/token/me` | JWT | Get current user from token (alias) |

Both return: `TokenValidationResponse { valid, userId, email, role, tokenType, issuedAt, expiresAt }`

---

### 5.8 AdminUserController — `/api/admin/users`

| # | Method | Path | Auth | Purpose |
|---|--------|------|------|---------|
| 39 | `POST` | `/api/admin/users` | ADMIN | Create user with any role |
| 40 | `PATCH` | `/api/admin/users/{userId}/status` | ADMIN | Enable/disable account |

**Endpoint 39: POST /api/admin/users**
- Request: `AdminCreateUserRequest { email: @NotBlank @Email, phoneNumber?, password: @NotBlank @Size(8-100), fullName: @NotBlank @Size(2-100), role: @NotNull }`
- Response 201: `UserProfileResponse`
- Guard: `@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")`

**Endpoint 40: PATCH /api/admin/users/{userId}/status**
- Request: `UpdateUserStatusRequest { isActive: @NotNull Boolean }`
- Response 200: `UserProfileResponse`
- Guard: `@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")`

---

### 5.9 Spring-Managed Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/oauth2/authorize` | No | OAuth2 authorization redirect |
| `GET` | `/oauth2/callback/google` | No | Google OAuth2 callback |
| `GET` | `/login/oauth2/**` | No | OAuth2 login flows |
| `GET` | `/actuator/health` | No | Spring Actuator health |
| `GET` | `/actuator/info` | No | App info |
| `GET` | `/actuator/metrics` | JWT | Metrics (protected) |
| `GET` | `/swagger-ui.html` | — | Swagger UI (disabled in prod) |
| `GET` | `/v3/api-docs` | — | OpenAPI JSON (disabled in prod) |

---

## 6. RESPONSE DTO STRUCTURES

### `LoginResponse`
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "550e8400-e29b-...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400,
  "isNewUser": false,
  "phoneNumber": "9356011874",
  "isPhoneVerified": true,
  "isEmailVerified": false
}
```

### `UserProfileResponse`
```json
{
  "userId": 1,
  "email": "user@example.com",
  "phoneNumber": "9356011874",
  "fullName": "John Doe",
  "role": "USER",
  "isActive": true,
  "isEmailVerified": true,
  "isPhoneVerified": true,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00",
  "lastLoginAt": "2024-01-15T10:30:00",
  "hasPassword": true
}
```

### `TokenValidationResponse`
```json
{
  "valid": true,
  "userId": 1,
  "email": "user@example.com",
  "role": "USER",
  "tokenType": "Bearer",
  "issuedAt": 1673779800,
  "expiresAt": 1673866200
}
```

### `SessionResponse`
```json
{
  "id": 1,
  "sessionId": "1",
  "deviceId": "abc-123",
  "deviceName": "iPhone 15",
  "deviceModel": "iPhone15,2",
  "platform": "ios",
  "systemVersion": "17.2",
  "appVersion": "1.0.0",
  "ipAddress": "203.0.113.45",
  "location": null,
  "isTrusted": true,
  "isCurrentSession": true,
  "lastActivityAt": "2024-01-15T10:30:00",
  "createdAt": "2024-01-10T08:00:00"
}
```

### `ErrorResponse`
```json
{
  "status": 401,
  "error": "Authentication Failed",
  "message": "Invalid credentials",
  "path": "/api/auth/login",
  "validationErrors": {
    "code": "AUTH_FAILED"
  }
}
```

---

## 7. SECURITY ARCHITECTURE

### 7.1 JWT Configuration
| Setting | Value |
|---------|-------|
| Algorithm | HS512 (HMAC SHA-512) |
| Library | JJWT 0.11.5 |
| Secret | `${JWT_SECRET}` (REQUIRED, no default) |
| Access token expiry | 86400000 ms (24 hours) |
| Refresh token expiry | 7 days |
| Issuer | `fixhomi-auth-service` |
| Subject | user email |
| Custom claims | `userId` (Long), `role` (String), `tokenType` ("ACCESS") |

### 7.2 JWT Authentication Filter (`JwtAuthenticationFilter`)
1. Extracts token from `Authorization: Bearer <token>` header
2. Validates signature + expiration via `JwtService.validateToken()`
3. **Database check:** Loads user by email — if not found → 401 `ACCOUNT_DELETED`
4. **Active check:** If `user.isActive == false` → 401 `ACCOUNT_DELETED`
5. Sets `SecurityContext` with email as principal, `ROLE_<role>` as authority

### 7.3 Password Encoding
- `BCryptPasswordEncoder` with strength **12**
- `passwordHash` column is 60 chars (standard BCrypt output)

### 7.4 Security Config (`SecurityConfig`)
- CSRF: **disabled** (stateless JWT)
- Session: `STATELESS`
- CORS: configured origins from `${ALLOWED_ORIGINS}` (no wildcard with credentials)
- HSTS: enabled, `max-age=31536000`, `includeSubDomains=true`
- Frame options: `DENY`
- Method security: `@PreAuthorize` enabled (`@EnableMethodSecurity`)
- Filter order: `RateLimitingFilter` (Order 1) → `JwtAuthenticationFilter` (before `UsernamePasswordAuthenticationFilter`)

### 7.5 Rate Limiting (`RateLimitingFilter`)
| Tier | Endpoints Matched | Limit |
|------|-------------------|-------|
| Auth | `/login`, `/register`, `/oauth2/google`, `/token/validate`, `/refresh` | 10 req/min per IP |
| OTP | `/otp`, `/forgot-password`, `/send-verification`, `/resend` | 5 req/min per IP |
| General | Everything else | 100 req/min per IP |

- **Skips:** GET requests (except those containing `/verify`)
- **IP detection:** Uses `getRemoteAddr()` in production; falls back to rightmost non-private IP from `X-Forwarded-For` in dev
- **Cleanup:** Buckets cleared every 30 minutes (`@Scheduled`)
- **Toggle:** `${RATE_LIMIT_ENABLED:true}`

### 7.6 Login Lockout
- **Threshold:** Configurable (entity uses `incrementAttempts(maxAttempts, lockoutDurationMinutes)`)
- **Tracking:** Per identifier (email/phone) AND per userId (unified cross-method lockout)
- **Storage:** Database-persisted (`LoginLockout` entity), survives restarts

### 7.7 CORS
- Allowed origins: comma-separated from `${ALLOWED_ORIGINS}`
- Wildcards (`*`) are filtered out (incompatible with `allowCredentials=true`)
- Allowed methods: GET, POST, PUT, DELETE, OPTIONS, PATCH
- Allowed headers: Authorization, Content-Type, Accept, Origin, X-Requested-With, Cache-Control
- Exposed headers: Authorization
- Max age: 3600s

---

## 8. EXTERNAL SERVICE INTEGRATIONS

### 8.1 Google OAuth2 (Mobile)
- **Service:** `GoogleAuthService`
- **Library:** `google-api-client` 2.2.0
- **Verifies against:** Web client ID, iOS client ID, Android client ID
- **Env vars:** `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_IOS_CLIENT_ID`, `GOOGLE_ANDROID_CLIENT_ID`
- **Behavior:** Auto-creates user on first sign-in; supports `mode` (login/signup) and `role` selection

### 8.2 Apple Sign-In (iOS)
- **Service:** `AppleAuthService`
- **Verification:** RS256 JWT verified against Apple's JWKS at `https://appleid.apple.com/auth/keys`
- **Env vars:** `APPLE_BUNDLE_ID`, `APPLE_SERVICE_ID` (optional)
- **Critical:** Email and name only provided on FIRST sign-in — must persist immediately

### 8.3 Email — Brevo (formerly Sendinblue)
- **Service:** `BrevoEmailService` implements `EmailService`
- **API:** `https://api.brevo.com/v3/smtp/email`
- **Env vars:** `BREVO_API_KEY`, `BREVO_SENDER_EMAIL` (default: `noreply@fixhomi.com`), `BREVO_SENDER_NAME` (default: `FixHomi`)
- **Toggle:** `EMAIL_PROVIDER=brevo` (default: `stub`)
- **Stub:** `StubEmailService` — logs to console in dev

### 8.4 SMS — MSG91
- **Service:** `Msg91SmsService` implements `SmsService`
- **API:** `https://control.msg91.com/api/v5/flow`
- **Env vars:** `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID`, `MSG91_VERIFICATION_TEMPLATE_ID`, `MSG91_DELETE_TEMPLATE_ID`, `MSG91_SENDER_ID`
- **Toggle:** `SMS_PROVIDER=msg91` (default: `stub`)
- **Stub:** `StubSmsService` — logs to console in dev

### 8.5 Node.js Backend
- **URL:** `${NODEJS_BACKEND_URL:http://localhost:5001}`
- **Integration:** Node.js calls `/api/token/validate` to verify JWTs
- **Shared secret:** Both services use the same `JWT_SECRET`

---

## 9. KEY BUSINESS LOGIC

### 9.1 Refresh Token Rotation
On every `/api/auth/refresh` call:
1. Find existing refresh token by value
2. Verify it's valid (not revoked, not expired)
3. Revoke old token
4. Create new refresh token (UUID)
5. Check user is still active (if not, revoke new token too, throw 401)
6. Generate new access token
7. Return both

### 9.2 Phone Number Normalization
All phone numbers are auto-normalized on JPA persist/update:
- Strip all non-digit characters
- If >10 digits and starts with `91`, strip the `91` prefix
- Store as exactly 10 digits
- `del_*` prefixes preserved (soft-delete marker)

### 9.3 Email Anti-Enumeration
- `POST /api/auth/forgot-password` always returns 200 regardless of whether email exists
- `POST /api/auth/forgot-password/email` always returns 200

### 9.4 OAuth Mode Support
Both Google and Apple mobile endpoints support a `mode` field:
- `"login"` — only authenticate existing users, error if not found
- `"signup"` — only register new users, error if already exists
- `null`/empty — legacy auto-register-or-login behavior

### 9.5 Password Requirements
Validated via regex on DTOs:
- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character (`@$!%*?&#^()_+-=`)

### 9.6 Account Deletion Flow
1. `POST /api/users/delete-account/request-otp` → sends OTP to verified phone
2. `DELETE /api/users/account` with `{ otp, reason? }` → verifies OTP, deletes account

### 9.7 Email Verification Flow
1. `POST /api/auth/email/send-verification` (JWT) → sends email with link
2. User clicks link → `GET /api/auth/email/verify?token=...`
3. Returns HTML page with success/error + auto-redirect via deep link (`fixhomi://email-verified`)

---

## 10. EXCEPTION HANDLING

`GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP status codes:

| Exception | Status | Error Title |
|-----------|--------|------------|
| `AuthenticationException` | 401 | Authentication Failed |
| `ResourceNotFoundException` | 404 | Resource Not Found |
| `DuplicateResourceException` | 409 | Resource Already Exists (includes `conflictField`) |
| `AccessDeniedException` | 403 | Access Denied |
| `InvalidPasswordException` | 400 | Invalid Password |
| `InvalidRoleException` | 403 | Invalid Role |
| `VerificationException` | 400 | Verification Failed |
| `TooManyRequestsException` | 429 | Too Many Requests (includes `retryAfterSeconds` if available) |
| `MethodArgumentNotValidException` | 400 | Validation Failed (field-level errors) |
| `Exception` (catch-all) | 500 | Internal Server Error (generic message, full stack logged) |

Structured error codes in AuthenticationException: `ROLE_CONFLICT`, `NOT_REGISTERED`, `ALREADY_REGISTERED` (parsed from `"CODE:ROLE:message"` format)

---

## 11. CONFIGURATION REFERENCE

### 11.1 Required Environment Variables
| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `JWT_SECRET` | **YES** | none | JWT signing key (must be >= 256 bits for HS512) |
| `DATABASE_HOST` | prod | — | PostgreSQL host |
| `DATABASE_NAME` | prod | — | Database name |
| `DATABASE_USERNAME` | prod | — | DB username |
| `DATABASE_PASSWORD` | prod | — | DB password |

### 11.2 Optional Environment Variables
| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | (default) | `prod` for production |
| `PORT` | `8080` | Server port |
| `GOOGLE_CLIENT_ID` | empty | Google OAuth web client |
| `GOOGLE_CLIENT_SECRET` | empty | Google OAuth web secret |
| `GOOGLE_IOS_CLIENT_ID` | empty | Google OAuth iOS client |
| `GOOGLE_ANDROID_CLIENT_ID` | empty | Google OAuth Android client |
| `APPLE_BUNDLE_ID` | empty | iOS app bundle ID for Apple Sign-In |
| `APPLE_SERVICE_ID` | empty | Apple Services ID (web) |
| `EMAIL_PROVIDER` | `stub` | `stub` or `brevo` |
| `BREVO_API_KEY` | empty | Brevo email API key |
| `BREVO_SENDER_EMAIL` | `noreply@fixhomi.com` | From address |
| `BREVO_SENDER_NAME` | `FixHomi` | From name |
| `SMS_PROVIDER` | `stub` | `stub` or `msg91` |
| `MSG91_AUTH_KEY` | empty | MSG91 auth key |
| `MSG91_TEMPLATE_ID` | empty | Default OTP template |
| `MSG91_VERIFICATION_TEMPLATE_ID` | empty | Phone verification template |
| `MSG91_DELETE_TEMPLATE_ID` | empty | Account deletion template |
| `MSG91_SENDER_ID` | empty | SMS sender ID |
| `RATE_LIMIT_ENABLED` | `true` | Toggle rate limiting |
| `ALLOWED_ORIGINS` | `http://localhost:3000,...` | CORS origins (comma-separated) |
| `FIXHOMI_BASE_URL` | `https://jauth.onrender.com` | Email verification links base |
| `FIXHOMI_FRONTEND_URL` | `https://jauth.onrender.com` | Password reset links base |
| `FIXHOMI_DEEP_LINK_SCHEME` | `fixhomi` | App deep link scheme |
| `NODEJS_BACKEND_URL` | `http://localhost:5001` | Node.js backend URL |

---

## 12. FILE STRUCTURE MAP

```
jauth/
├── pom.xml                                          # Maven build config
├── render.yaml                                      # Render.com deployment
├── .env.example                                     # Env var template
├── src/main/
│   ├── java/com/fixhomi/auth/
│   │   ├── AuthServiceApplication.java              # Spring Boot entry point
│   │   ├── config/
│   │   │   ├── SecurityConfig.java                  # Spring Security filter chain + CORS
│   │   │   ├── RateLimitingFilter.java              # Bucket4j IP rate limiting
│   │   │   ├── JwtProperties.java                   # JWT config binding
│   │   │   ├── FixhomiProperties.java               # App config binding (OTP, email, SMS)
│   │   │   ├── EmailServiceConfig.java              # Conditional email provider bean
│   │   │   ├── SmsServiceConfig.java                # Conditional SMS provider bean
│   │   │   ├── OpenApiConfig.java                   # Swagger/OpenAPI setup
│   │   │   └── JpaConfig.java                       # JPA auditing config
│   │   ├── controller/
│   │   │   ├── AuthController.java                  # Login, register, refresh, logout
│   │   │   ├── OtpLoginController.java              # Passwordless OTP login
│   │   │   ├── OAuth2Controller.java                # Google/Apple mobile sign-in
│   │   │   ├── VerificationController.java          # Phone/email verify, password reset
│   │   │   ├── SessionController.java               # Sessions, trusted devices, token validate
│   │   │   ├── UserController.java                  # Profile CRUD, password change, delete account
│   │   │   ├── TokenController.java                 # JWT validation for Node.js
│   │   │   └── AdminUserController.java             # Admin user creation/status
│   │   ├── dto/                                     # 22 request/response DTOs
│   │   │   ├── LoginRequest.java                    # { email, password }
│   │   │   ├── PhoneLoginRequest.java               # { phoneNumber, password }
│   │   │   ├── RegisterRequest.java                 # { email, phoneNumber?, password, fullName, role }
│   │   │   ├── LoginResponse.java                   # { accessToken, refreshToken, userId, email, ... }
│   │   │   ├── RefreshTokenRequest.java             # { refreshToken }
│   │   │   ├── LogoutRequest.java                   # { refreshToken }
│   │   │   ├── GoogleMobileAuthRequest.java         # { idToken, role?, mode?, deviceId?, ... }
│   │   │   ├── AppleMobileAuthRequest.java          # { identityToken, fullName?, email?, role?, mode?, ... }
│   │   │   ├── PhoneOtpLoginRequest.java            # { phoneNumber }
│   │   │   ├── PhoneOtpVerifyRequest.java           # { phoneNumber, otp }
│   │   │   ├── EmailOtpLoginRequest.java            # { email }
│   │   │   ├── EmailOtpVerifyRequest.java           # { email, otp }
│   │   │   ├── ForgotPasswordRequest.java           # { email }
│   │   │   ├── ForgotPasswordPhoneRequest.java      # { phoneNumber }
│   │   │   ├── ResetPasswordRequest.java            # { token, newPassword }
│   │   │   ├── VerifyOtpAndResetPasswordRequest.java    # { phoneNumber, otp, newPassword }
│   │   │   ├── VerifyEmailOtpAndResetPasswordRequest.java # { email, otp, newPassword }
│   │   │   ├── VerifyOtpRequest.java                # { otp }
│   │   │   ├── ChangePasswordRequest.java           # { currentPassword?, newPassword }
│   │   │   ├── UpdateProfileRequest.java            # { fullName?, phoneNumber? }
│   │   │   ├── DeleteAccountRequest.java            # { otp, reason? }
│   │   │   ├── DeviceInfoRequest.java               # { deviceId, deviceName?, ... }
│   │   │   ├── RevokeAllSessionsRequest.java        # { exceptDeviceId? }
│   │   │   ├── AdminCreateUserRequest.java          # { email, phoneNumber?, password, fullName, role }
│   │   │   ├── UpdateUserStatusRequest.java         # { isActive }
│   │   │   ├── UserProfileResponse.java             # { userId, email, ..., hasPassword }
│   │   │   ├── TokenValidationResponse.java         # { valid, userId, email, role, ... }
│   │   │   ├── SessionResponse.java                 # { id, deviceId, platform, ... }
│   │   │   ├── VerificationResponse.java            # { success, message, maskedContact? }
│   │   │   └── MessageResponse.java                 # { message }
│   │   ├── entity/                                  # 12 JPA entities (see section 4)
│   │   ├── repository/                              # 11 JPA repositories
│   │   │   ├── UserRepository.java
│   │   │   ├── RefreshTokenRepository.java
│   │   │   ├── UserSessionRepository.java
│   │   │   ├── TrustedDeviceRepository.java
│   │   │   ├── PhoneOtpRepository.java
│   │   │   ├── EmailOtpRepository.java
│   │   │   ├── PasswordResetOtpRepository.java
│   │   │   ├── PasswordResetTokenRepository.java
│   │   │   ├── EmailVerificationTokenRepository.java
│   │   │   ├── LoginLockoutRepository.java
│   │   │   └── DeleteAccountOtpRepository.java
│   │   ├── security/
│   │   │   ├── JwtService.java                      # JWT create/validate (HS512)
│   │   │   ├── JwtAuthenticationFilter.java         # Request filter: token → SecurityContext
│   │   │   ├── OAuth2AuthenticationSuccessHandler.java
│   │   │   └── OAuth2AuthenticationFailureHandler.java
│   │   ├── service/
│   │   │   ├── AuthService.java                     # Login/register logic + lockout
│   │   │   ├── RefreshTokenService.java             # Token rotation + revocation
│   │   │   ├── SessionService.java                  # Session + device management
│   │   │   ├── UserService.java                     # Profile CRUD, password, deletion
│   │   │   ├── GoogleAuthService.java               # Google ID token verification
│   │   │   ├── AppleAuthService.java                # Apple identity token verification
│   │   │   ├── OtpLoginService.java                 # Passwordless OTP login flows
│   │   │   ├── PhoneVerificationService.java        # Phone OTP verify
│   │   │   ├── EmailVerificationService.java        # Email token verify
│   │   │   ├── PasswordResetService.java            # All password reset flows
│   │   │   ├── TokenValidationService.java          # Token introspection for Node.js
│   │   │   └── notification/
│   │   │       ├── EmailService.java                # Interface
│   │   │       ├── BrevoEmailService.java           # Brevo API implementation
│   │   │       ├── StubEmailService.java            # Dev: logs to console
│   │   │       ├── SmsService.java                  # Interface
│   │   │       ├── Msg91SmsService.java             # MSG91 API implementation
│   │   │       └── StubSmsService.java              # Dev: logs to console
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java          # @RestControllerAdvice
│   │       ├── ErrorResponse.java                   # Error JSON structure
│   │       ├── AuthenticationException.java
│   │       ├── ResourceNotFoundException.java
│   │       ├── DuplicateResourceException.java
│   │       ├── InvalidPasswordException.java
│   │       ├── InvalidRoleException.java
│   │       ├── TooManyRequestsException.java
│   │       └── VerificationException.java
│   └── resources/
│       ├── application.yaml                         # Default/dev config
│       └── application-prod.yaml                    # Production overrides
└── src/test/
    └── java/.../AuthServiceApplicationTests.java    # Boot context test
```

---

## 13. TOTAL COUNTS

| Category | Count |
|----------|-------|
| Controllers | 8 |
| API Endpoints | 40 (including Spring-managed) |
| JPA Entities | 12 |
| Repositories | 11 |
| Services | 11 + 4 notification (2 interfaces + 4 impls) |
| DTOs | 22 request + 7 response = 29 |
| Custom Exceptions | 7 + ErrorResponse |
| Config Classes | 8 |
