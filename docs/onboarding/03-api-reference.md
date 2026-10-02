# 03 · API Reference

**50 endpoints in 9 controllers**, plus the browser Google OAuth2 flow and Actuator. All of them are in the Postman collection (`postman/`) in the same order as below, and all were exercised against a local instance.

Legend:
- **Access**: 🌐 public · 🔑 any valid JWT · 🛡️ JWT with role ADMIN or IT_ADMIN
- **Bucket**: the rate-limit bucket ([details](04-security-jwt-rbac.md#6-rate-limiting)). `auth` = 10/min, `otp` = 5/min, `general` = 100/min, `—` = not limited (most GETs).

All paths are relative to `http://localhost:8080` locally and `https://auth.fixhomi.com` in production.

---

## Summary table

| # | Method | Path | Access | Bucket | Controller → Service |
|---|---|---|---|---|---|
| 1 | POST | `/api/auth/register` | 🌐 | auth | AuthController → AuthService.register |
| 2 | POST | `/api/auth/login` | 🌐 | auth | AuthController → AuthService.login |
| 3 | POST | `/api/auth/login/phone` | 🌐 | auth | AuthController → AuthService.loginWithPhone |
| 4 | POST | `/api/auth/refresh` | 🌐 | auth | AuthController → RefreshTokenService.rotateRefreshToken |
| 5 | POST | `/api/auth/logout` | 🌐 | general | AuthController → RefreshTokenService.revokeToken |
| 6 | GET | `/api/auth/health` | 🌐 | — | AuthController |
| 7 | POST | `/api/auth/login/phone/send-otp` | 🌐 | otp | OtpLoginController → OtpLoginService |
| 8 | POST | `/api/auth/login/phone/verify` | 🌐 | auth | OtpLoginController → OtpLoginService |
| 9 | POST | `/api/auth/login/email/send-otp` | 🌐 | otp | OtpLoginController → OtpLoginService |
| 10 | POST | `/api/auth/login/email/verify` | 🌐 | auth | OtpLoginController → OtpLoginService |
| 11 | POST | `/api/auth/signup/phone/send-otp` | 🌐 | otp | PhoneSignupController → PhoneSignupService |
| 12 | POST | `/api/auth/signup/phone/verify` | 🌐 | general | PhoneSignupController → PhoneSignupService |
| 13 | POST | `/api/auth/oauth2/google/mobile` | 🌐 | auth | OAuth2Controller → GoogleAuthService |
| 14 | POST | `/api/auth/oauth2/apple/mobile` | 🌐 | general | OAuth2Controller → AppleAuthService |
| 15 | POST | `/api/auth/oauth2/apple/send-email-otp` | 🌐 | general | OAuth2Controller → AppleEmailVerificationService |
| 16 | POST | `/api/auth/oauth2/apple/verify-email-otp` | 🌐 | general | OAuth2Controller → AppleEmailVerificationService |
| 17 | POST | `/api/auth/otp/send` | 🔑 | otp | VerificationController → PhoneVerificationService |
| 18 | POST | `/api/auth/otp/verify` | 🔑 | otp | VerificationController → PhoneVerificationService |
| 19 | POST | `/api/auth/email/send-verification` | 🔑 | otp | VerificationController → EmailVerificationService |
| 20 | GET | `/api/auth/email/verify?token=` | 🌐 | general | VerificationController → EmailVerificationService |
| 21 | POST | `/api/auth/forgot-password` | 🌐 | otp | VerificationController → PasswordResetService |
| 22 | GET | `/api/auth/reset-password/validate?token=` | 🌐 | — | VerificationController → PasswordResetService |
| 23 | POST | `/api/auth/reset-password` | 🌐 | general | VerificationController → PasswordResetService |
| 24 | POST | `/api/auth/forgot-password/phone` | 🌐 | otp | VerificationController → PasswordResetService |
| 25 | POST | `/api/auth/forgot-password/phone/verify` | 🌐 | otp | VerificationController → PasswordResetService |
| 26 | POST | `/api/auth/forgot-password/email` | 🌐 | otp | VerificationController → PasswordResetService |
| 27 | POST | `/api/auth/forgot-password/email/verify` | 🌐 | otp | VerificationController → PasswordResetService |
| 28 | GET | `/api/auth/sessions` | 🔑 | — | SessionController → SessionService |
| 29 | DELETE | `/api/auth/sessions/{sessionId}` | 🔑 | general | SessionController → SessionService |
| 30 | POST | `/api/auth/sessions/revoke-all` | 🔑 | general | SessionController → SessionService |
| 31 | GET | `/api/auth/validate` | 🔑 | — | SessionController (reads DB user) |
| 32 | POST | `/api/auth/devices/trust` | 🔑 | general | SessionController → SessionService |
| 33 | GET | `/api/auth/devices/trust` | 🔑 | — | SessionController → SessionService |
| 34 | DELETE | `/api/auth/devices/trust/{deviceId}` | 🔑 | general | SessionController → SessionService |
| 35 | GET | `/api/token/validate` | 🔑 | — | TokenController → TokenValidationService |
| 36 | GET | `/api/token/me` | 🔑 | — | TokenController → TokenValidationService |
| 37 | GET | `/api/users/me` | 🔑 | — | UserController → UserService.getUserProfile |
| 38 | PUT | `/api/users/profile` | 🔑 | general | UserController → UserService.updateProfile |
| 39 | POST | `/api/users/email` | 🔑 | general | UserController → UserService.setEmail |
| 40 | POST | `/api/users/phone/change/send-otp` | 🔑 | otp | UserController → PhoneVerificationService |
| 41 | POST | `/api/users/phone/change/verify` | 🔑 | general | UserController → PhoneVerificationService |
| 42 | POST | `/api/users/change-password` | 🔑 | general | UserController → UserService.changePassword |
| 43 | POST | `/api/users/delete-account/request-otp` | 🔑 | general | UserController → UserService |
| 44 | DELETE | `/api/users/account` | 🔑 | general | UserController → UserService.deleteAccountWithOtp |
| 45 | DELETE | `/api/users/{userId}` | 🔑 self or DB-role ADMIN | general | UserController → UserService.deleteAccountById |
| 46 | PATCH | `/api/admin/users/{userId}/status` | 🛡️ | general | AdminUserController → UserService.updateUserStatus |
| 47 | POST | `/api/admin/users` | 🛡️ | general | AdminUserController → UserService.createUserByAdmin |
| 48 | GET | `/api/admin/users/list` | 🛡️ | — | AdminUserController → UserService.listUsers |
| 49 | GET | `/api/admin/users/{userId}` | 🛡️ | — | AdminUserController → UserService.getUserById |
| 50 | DELETE | `/api/admin/users/{userId}` | 🛡️ | general | AdminUserController → UserService.hardDeleteUserById |

Other HTTP surfaces:

| Path | Access | Notes |
|---|---|---|
| `GET /oauth2/authorize/google` | 🌐 | Browser Google login (Spring `oauth2Login`). Redirects to Google. |
| `GET /oauth2/callback/google` | 🌐 | OAuth2 callback; responds with `LoginResponse` JSON (`OAuth2AuthenticationSuccessHandler`) |
| `GET /actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info` | 🌐 | Render health check |
| `GET /actuator/metrics` | 🔑 | Exposed but needs a JWT |
| `/swagger-ui.html`, `/v3/api-docs` | 🔑 | Not in the permit list, so a JWT is needed locally; disabled in prod |
| `/h2-console` | 🔑 | Enabled in base config but blocked by security and `X-Frame-Options: DENY`; use the IntelliJ DB tool instead |

Totals: 26 public, 24 require a JWT, and 5 of those 24 also require ADMIN/IT_ADMIN.

---

## Shared response shapes

### `LoginResponse`
Returned by register, every login, refresh, OTP verify, phone signup, Google/Apple.

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "37681e7d-b7d1-4dfd-ba0c-52b04cde47ff6ef5a8996eb2483d90b9ec5ad799efb7",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "tej.user@example.com",
  "fullName": "Tej User",
  "role": "USER",
  "expiresIn": 86400,
  "isNewUser": false,
  "phoneNumber": "9876543210",
  "isPhoneVerified": false,
  "isEmailVerified": false
}
```
- `expiresIn` is in **seconds** (access token lifetime).
- `refreshToken` is opaque: two UUIDs concatenated, 68 characters. It is not a JWT.
- `refresh`, Google and Apple use shorter constructors, so `phoneNumber` / `isPhoneVerified` / `isEmailVerified` (and on refresh also `isNewUser`) are `null`.
- `email` is `null` for accounts created by phone signup.

### `UserProfileResponse`
`GET /api/users/me`, profile/email/phone updates, admin endpoints.
```json
{
  "userId": 1, "email": "tej.user@example.com", "phoneNumber": "9876543210",
  "fullName": "Tej User", "role": "USER", "isActive": true,
  "isEmailVerified": false, "isPhoneVerified": false,
  "createdAt": "2026-10-02T17:41:08.763957", "updatedAt": "2026-10-02T17:41:21.027489",
  "lastLoginAt": "2026-10-02T17:41:21.025867", "hasPassword": true
}
```

### `TokenValidationResponse`
`GET /api/token/validate` and `/api/token/me`.
```json
{ "valid": true, "userId": 1, "email": "tej.user@example.com", "role": "USER",
  "tokenType": "ACCESS", "issuedAt": 1790943081, "expiresAt": 1791029481 }
```

### Errors: three shapes

1. **`ErrorResponse`** from `GlobalExceptionHandler`, used by most endpoints:
   ```json
   { "timestamp": "2026-10-02T17:41:20.251436", "status": 403, "error": "Invalid Role",
     "message": "Role 'ADMIN' is not allowed: Public registration only allows USER or SERVICE_PROVIDER roles",
     "path": "/api/auth/register", "validationErrors": null }
   ```
   `validationErrors` holds per-field messages for 400 validation failures, plus extra keys in some cases: `code`, `existingRole`, `conflictField`, `retryAfterSeconds`.

   | Exception | Status | `error` |
   |---|---|---|
   | `AuthenticationException` | 401 | Authentication Failed (`validationErrors.code` = `AUTH_FAILED` or `ROLE_CONFLICT` / `NOT_REGISTERED` / `ALREADY_REGISTERED` / `EMAIL_REQUIRED`) |
   | `ResourceNotFoundException` | 404 | Resource Not Found |
   | `DuplicateResourceException` | 409 | Resource Already Exists (`conflictField`) |
   | `AccessDeniedException` (from `@PreAuthorize`) | 403 | Access Denied |
   | `InvalidPasswordException` | 400 | Invalid Password |
   | `InvalidRoleException` | 403 | Invalid Role |
   | `VerificationException` | 400 | Verification Failed |
   | `TooManyRequestsException` | 429 | Too Many Requests (`retryAfterSeconds` when the message contains "wait N seconds") |
   | `MethodArgumentNotValidException` | 400 | Validation Failed |
   | anything else | 500 | Internal Server Error (this includes malformed JSON and an unknown `role` value) |

2. **Ad-hoc maps** from `OtpLoginController`, `PhoneSignupController` and the phone/email forgot-password endpoints:
   `{"success": false, "code": "INVALID_OTP" | "OTP_EXPIRED" | "MAX_ATTEMPTS_EXCEEDED" | "USER_NOT_FOUND" | "TOO_MANY_REQUESTS" | "ALREADY_REGISTERED" | "VERIFICATION_FAILED" | "GOOGLE_ACCOUNT", "message": "..."}`

3. **Written directly by filters**, outside Spring MVC:
   - JWT filter: `401 {"message":"Account no longer exists." | "Account has been deactivated.","code":"ACCOUNT_DELETED"}`
   - Rate limiter: `429 {"status":429,"error":"Too Many Requests","message":"...","path":"..."}`
   - No/invalid token, or wrong role: **`302` redirect to `/oauth2/authorize/google`**. See [04 §4](04-security-jwt-rbac.md#4-what-a-denied-request-looks-like).

---

## Validation rules you'll meet

| Field | Rule | Where |
|---|---|---|
| Password (register, reset by link, change) | 8–100 chars, `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_+\-=])[A-Za-z\d@$!%*?&#^()_+\-=]{8,}$` | `RegisterRequest`, `ResetPasswordRequest`, `ChangePasswordRequest` |
| Password (reset by phone/email OTP) | 8–128 chars, upper + lower + digit + any non-alphanumeric | `VerifyOtpAndResetPasswordRequest`, `VerifyEmailOtpAndResetPasswordRequest` |
| Password (admin create, login) | 8–100 chars only | `AdminCreateUserRequest`, `LoginRequest` |
| Phone (register, login, OTP, signup) | `^\+?[1-9]\d{6,14}$` | most DTOs |
| Phone (forgot-password phone) | `^[+]?[0-9]{10,15}$` | `ForgotPasswordPhoneRequest` |
| Phone (change) | `^[+\d][\d\s-]{7,17}$` | `PhoneChange*Request` |
| OTP | 4–10 digits on login/signup/verify; exactly 6 on reset/delete/Apple | per DTO |
| Full name | 2–100 chars | `RegisterRequest`, `UpdateProfileRequest`, `AdminCreateUserRequest` |

Stored phone numbers are always normalised to 10 digits (`+919876543210` → `9876543210`).

---

## Endpoint details

### AuthController: `/api/auth`

**1. `POST /register`** → **201** `LoginResponse`
```json
{ "email": "a@b.com", "phoneNumber": "+919876543210", "password": "Passw0rd!23",
  "fullName": "Tej User", "role": "USER" }
```
- `role` is required and must be `USER` or `SERVICE_PROVIDER`; anything else → 403 *Invalid Role*.
- 409 if a **verified** active account has the email, or a verified account has the phone. If an *unverified* account holds the email, that account is deactivated and its email released ("reclaim").
- Sends no email. `email` must be lowercase-safe (see gotchas).

**2. `POST /login`** `{email, password}` → 200 `LoginResponse`
- 401 *Invalid email or password*; 401 *Account is deactivated*; 429 after 5 failures (15-minute lock, `login_lockouts` table).

**3. `POST /login/phone`** `{phoneNumber, password}` → 200. Same lockout. Phone-signup accounts have no password, so this always returns 401 for them.

**4. `POST /refresh`** `{refreshToken}` → 200 `LoginResponse` with a **new refresh token** (rotation). 401 if the token is unknown, revoked or expired. Replaying the previous token within 45 s returns the same successor.

**5. `POST /logout`** `{refreshToken}` → 200 `{"message":"Logged out successfully"}`, always. It revokes that refresh token only.

**6. `GET /health`** → `{"status":"UP","message":"Auth service is running"}`

### OtpLoginController: `/api/auth/login`

**7. `POST /phone/send-otp`** `{phoneNumber}` → `{"success":true,"message":"OTP sent successfully to ****3210","maskedPhone":"****3210","expiresInMinutes":5}`
404 `USER_NOT_FOUND` (no account, or disabled) · 429 `TOO_MANY_REQUESTS` (3 sends / 5 min / phone) · 500 if SMS fails. A full trace is in [08](08-walkthrough-send-otp-and-validate-token.md).

**8. `POST /phone/verify`** `{phoneNumber, otp}` → `LoginResponse`. 400 `INVALID_OTP` / `OTP_EXPIRED` / `MAX_ATTEMPTS_EXCEEDED` (3 attempts).

**9. `POST /email/send-otp`** `{email}` → `{success, message, maskedEmail, expiresInMinutes}`. **10. `POST /email/verify`** `{email, otp}` → `LoginResponse`; also sets `isEmailVerified=true`.

### PhoneSignupController: `/api/auth/signup`

**11. `POST /phone/send-otp`** `{phoneNumber, fullName?}` → 200. 409 `ALREADY_REGISTERED` if a verified account has the phone. No user row is created yet; the pending signup is stored in `phone_signup_otps`.

**12. `POST /phone/verify`** `{phoneNumber, otp}` → `LoginResponse` with `isNewUser: true`, `role: USER`, `email: null`. Re-checks for a race (409) before creating the user.

### OAuth2Controller: `/api/auth/oauth2`

**13. `POST /google/mobile`** `{idToken, role?, mode?, deviceId?, deviceType?, appVersion?}`
- `idToken` comes from the Google SDK; it is verified against `GOOGLE_CLIENT_ID` / `GOOGLE_IOS_CLIENT_ID` / `GOOGLE_ANDROID_CLIENT_ID`.
- `role` is `USER` | `SERVICE_PROVIDER` (default USER). `mode` is `login` | `signup` | empty (empty = log in, or create if missing).
- Accounts are linked **by email**. 401 codes: `ROLE_CONFLICT` (existing account has another role; `existingRole` is included), `NOT_REGISTERED`, `ALREADY_REGISTERED`.

**14. `POST /apple/mobile`** `{identityToken | verificationToken, appleUserId?, email?, fullName?, role?, mode?}`. Verifies Apple's RS256 token against Apple's JWKS and `APPLE_BUNDLE_ID`. Returns 401 "not configured" when `APPLE_BUNDLE_ID` is empty (as it is locally). Also returns `EMAIL_REQUIRED` (with `existingRole` = appleUserId) when Apple did not share an email.

**15. `POST /apple/send-email-otp`** `{email, appleUserId}` → `{"message":"Verification code sent to ..."}`. **16. `POST /apple/verify-email-otp`** `{email, appleUserId, otp}` → `{"verified":true,"verificationToken":"<HS512, 10 min>"}`. The app then calls #14 with that `verificationToken`.

### VerificationController: `/api/auth`

**17. `POST /otp/send`** (no body) sends an OTP to the caller's stored phone. 400 if there is no phone, it is already verified, or there are too many requests (note: 400, not 429, here).
**18. `POST /otp/verify`** `{otp}` sets `isPhoneVerified=true`.
**19. `POST /email/send-verification`** (no body) emails a link. 429 with `retryAfterSeconds` if called again within 5 minutes.
**20. `GET /email/verify?token=`** returns an **HTML page**, always with status 200, that redirects to `fixhomi://email-verified?email=…&status=success|error`.
**21. `POST /forgot-password`** `{email}` always returns 200, so it does not reveal whether the account exists. It emails a reset link; the token is valid 1 h and stored as SHA-256.
**22. `GET /reset-password/validate?token=`** → 200 or 400.
**23. `POST /reset-password`** `{token, newPassword}` → 200; revokes all refresh tokens.
**24. `POST /forgot-password/phone`** `{phoneNumber}` → `{success, message, maskedPhone, expiresInMinutes}`; 404 `USER_NOT_FOUND` for an unknown phone or an account without a password.
**25. `POST /forgot-password/phone/verify`** `{phoneNumber, otp, newPassword}` → 200.
**26. `POST /forgot-password/email`** `{email}` → 200 (generic); 400 `GOOGLE_ACCOUNT` when the account has no password.
**27. `POST /forgot-password/email/verify`** `{email, otp, newPassword}` → 200.

### SessionController: `/api/auth`

**28. `GET /sessions`** (optional header `X-Device-Id`) → `{"sessions":[...],"count":n}`
**29. `DELETE /sessions/{sessionId}`** → `{success, message}`; 401 "Session not found" / "Not authorized to revoke this session".
**30. `POST /sessions/revoke-all`** `{exceptDeviceId?}` → `{success, message, revokedCount}`
**31. `GET /validate`** → `{"valid":true,"userId":1,"email":"…","role":"USER","isEmailVerified":false,"isPhoneVerified":false}`. Role and flags come from the **database**.
**32. `POST /devices/trust`** `{deviceId, deviceName?, deviceModel?, platform?, systemVersion?, appVersion?, buildNumber?, customName?}` → `{success, message, deviceId, trustedAt}`
**33. `GET /devices/trust`** → `{"devices":[...],"count":n}` · **34. `DELETE /devices/trust/{deviceId}`**

> No login flow writes `user_sessions` today, so #28 returns an empty list and #29 returns 401. See [10](10-behaviours-and-gotchas.md).

### TokenController: `/api/token`

**35. `GET /validate`** and **36. `GET /me`** run identical code and return `TokenValidationResponse` decoded from the JWT. The role here is the **JWT** role. Walkthrough: [08](08-walkthrough-send-otp-and-validate-token.md).

### UserController: `/api/users`

**37. `GET /me`** → `UserProfileResponse`.
**38. `PUT /profile`** `{fullName?, phoneNumber?}`. `phoneNumber` is ignored unless the account has no phone at all (legacy path for app builds before 1.0.5).
**39. `POST /email`** `{email}` sets an unverified email and sends a verification link; 409 if another active account uses it.
**40. `POST /phone/change/send-otp`** `{phoneNumber}` sends an OTP to the **new** number; the current number is unchanged.
**41. `POST /phone/change/verify`** `{phoneNumber, otp}` swaps the number and marks it verified.
**42. `POST /change-password`** `{currentPassword?, newPassword}` → `{"message":"Password changed successfully"}` (or "Password set successfully" for accounts without a password). It revokes all refresh tokens when a password existed before.
**43. `POST /delete-account/request-otp`** needs a verified phone; sends an SMS OTP.
**44. `DELETE /account`** `{otp, reason?}` (a DELETE with a body). Soft delete: refresh tokens revoked, email → `deleted_<id>@del.local`, phone → `del_<id>`, name → `[Deleted User]`, `isActive=false`.
**45. `DELETE /{userId}`** soft-deletes without an OTP. Allowed if the caller is that user, or the caller's **database** role is `ADMIN` (not IT_ADMIN). Otherwise 403 `{"message":"Not authorized to delete this account"}`.

### AdminUserController: `/api/admin/users`

Every method has `@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")`, and the URL rule in `SecurityConfig` adds the same check.

**46. `PATCH /{userId}/status`** `{isActive: true|false}` → `UserProfileResponse`. Disabling revokes all of that user's refresh tokens. Their access token is then rejected immediately by the JWT filter's `is_active` check.
**47. `POST /`** `{email, password, fullName, role, phoneNumber?}` → **201**. `role` must be `ADMIN`, `IT_ADMIN` or `SUPPORT`. The email is marked verified.
**48. `GET /list?role=&status=&search=&page=0&size=20`** → `{users, page, size, totalElements, totalPages}`, newest first. **Only `USER` and `SERVICE_PROVIDER` accounts are ever returned.** `role` accepts those two; `status` accepts `active` / `disabled`; `size` is clamped to 1–100.
**49. `GET /{userId}`** returns any user, staff included.
**50. `DELETE /{userId}`** **hard delete**. It removes the user row and their refresh tokens, sessions, trusted devices, delete-OTPs and lockouts. It cannot be undone.

Next: [04-security-jwt-rbac.md](04-security-jwt-rbac.md)
