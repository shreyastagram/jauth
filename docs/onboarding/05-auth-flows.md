# 05 · Authentication Flows

Each flow is shown as the steps the code actually performs, with the tables it touches. File references are relative to `src/main/java/com/fixhomi/auth/`. For the request/response fields see [03-api-reference.md](03-api-reference.md); for a deep line-by-line trace of one flow see [08](08-walkthrough-send-otp-and-validate-token.md).

Two building blocks appear everywhere:

- **Issuing tokens** = `jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole())` plus `refreshTokenService.createRefreshToken(user)`, which inserts a row in `refresh_tokens`. The result is wrapped in a `LoginResponse`.
- **Sending an OTP** = generate 6 digits with `SecureRandom`, invalidate earlier unused OTPs for the same target, insert a row with `expires_at = now + 5 min`, then call `SmsService.sendOtp(...)` or `EmailService.sendLoginOtp(...)`. Locally these print to the console.

---

## 1. Register (email + password)

`AuthController.register` → `AuthService.register` (AuthService.java:131–216)

1. **Role guard:** only `USER` / `SERVICE_PROVIDER` are allowed; anything else throws `InvalidRoleException` → 403.
2. **Email:**
   - A *verified* active account already has it → 409.
   - An *unverified* active account has it → that account is **deactivated**, and its email is renamed to `unclaimed_<id>@placeholder.local` ("reclaim").
3. **Phone:**
   - Normalised to 10 digits.
   - A verified holder exists → 409.
   - An unverified holder exists → its phone is cleared.
4. Insert into `users` with a BCrypt hash, `isActive=true`, and both verified flags `false`.
5. Issue tokens → **201** `LoginResponse`. No email is sent.

## 2. Login with password (email or phone)

`AuthService.login` / `loginWithPhone` (AuthService.java:65–125, 222–283). The method is `@Transactional(noRollbackFor = …)`, so failed-attempt counters are saved even though it throws.

1. If the identifier is locked in `login_lockouts` → 429 "Too many failed attempts. Please try again in N minutes."
2. Find the user. If none, record a failed attempt → 401 "Invalid email or password".
3. If any identifier of this user is locked ("unified lockout") → 429.
4. Inactive → 401 "Account is deactivated". This does not count as a failure.
5. `passwordEncoder.matches` fails → record a failed attempt. The 5th failure sets `locked_until = now + 15 min` → 401.
6. Success: clear the lockout, set `last_login_at`, issue tokens.

## 3. Refresh and logout

`AuthController.refresh` → `RefreshTokenService.rotateRefreshToken` (RefreshTokenService.java:79–122)

```
POST /api/auth/refresh {refreshToken: T1}
  T1 valid (not revoked, not expired)?
     yes → user inactive?  → revoke all user tokens, 401
           else create T2; mark T1 revoked, rotated_at=now, replaced_by_token=T2
     no  → T1 rotated ≤ 45 s ago and T2 still valid?  → return T2 again (grace window)
           else 401 "Invalid or expired refresh token"
  controller: re-check isActive, mint a new access token from the CURRENT DB role
```

- **The role is re-read from the database on refresh.** This is how a role change reaches a user's token.
- `POST /api/auth/logout {refreshToken}` sets `revoked=true` on that one row and always returns 200. Logout-revoked tokens get no grace window.

## 4. Passwordless OTP login

`OtpLoginController` → `OtpLoginService`. Works for **existing** accounts of any role; there is no role parameter.

**Phone**
1. `send-otp`: normalise the phone.
   - ≥ 3 OTP rows for this phone in the last 5 min → 429 `TOO_MANY_REQUESTS`.
   - No user → 404 `USER_NOT_FOUND`. Inactive → 404.
   - Otherwise insert a `phone_otps` row and send the SMS.
2. `verify`: take the newest unverified, unexpired row for the phone.
   - None → 400 `OTP_EXPIRED`.
   - `attempts++`; more than 3 → 400 `MAX_ATTEMPTS_EXCEEDED`.
   - Mismatch → 400 `INVALID_OTP`.
   - Match: mark the row verified, load the user, set `isPhoneVerified=true` if the number is still the account's number, update `last_login_at`, and issue tokens.

**Email:** the same steps on `email_otps`. A successful verify also sets `isEmailVerified=true`.

## 5. Phone signup (USER only)

`PhoneSignupController` → `PhoneSignupService`. Shipped for app build 1.0.5+ (the "unified one-tap" flow; history in `PHONE_SIGNUP_PLAN.md`).

1. `send-otp {phoneNumber, fullName?}`:
   - Rate limit (3 / 5 min).
   - If a **verified** active account of any role already has the phone → 409 `ALREADY_REGISTERED`.
   - An unverified holder loses the number.
   - Insert `phone_signup_otps (phone, otp, full_name, expires_at)`. **No user exists yet.**
2. `verify {phoneNumber, otp}`:
   - OTP checks as in §4.
   - **Re-check at commit time**, in case someone else registered the number in the meantime → 409.
   - Insert the user: `role = USER` (hard-coded, line 193), `email = NULL`, `passwordHash = NULL`, `isPhoneVerified = true`.
   - Issue tokens with `isNewUser: true`. The JWT has no `sub`, because there is no email.

The Node backend orchestrates this for the app: the app calls Node, Node calls these two endpoints, then creates the MongoDB profile with `_id = userId` ([07](07-how-other-services-use-auth.md)).

## 6. Google mobile sign-in

`GoogleAuthService.authenticateWithGoogle` (GoogleAuthService.java:100–254)

1. At startup the verifier is configured with the audiences `GOOGLE_CLIENT_ID`, `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_ANDROID_CLIENT_ID`.
2. Verify the `idToken` (signature, issuer, expiry, audience). The email must be present and `email_verified` must be true.
3. Requested role → USER or SERVICE_PROVIDER, defaulting to USER.
4. An unverified holder of the email is reclaimed (deactivated).
5. Find the user **by email**. Google login links to any account with that email, including an email+password one.
   - Existing user with a different role → 401 `ROLE_CONFLICT` (`existingRole` included).
   - Existing user and `mode=signup` → 401 `ALREADY_REGISTERED`.
   - No user and `mode=login` → 401 `NOT_REGISTERED`.
   - No user otherwise → create one with no password and `isEmailVerified=true`.
6. Issue tokens (`isNewUser` set).

## 7. Apple sign-in (with the email-OTP fallback)

`AppleAuthService` + `AppleEmailVerificationService`

```
App ── identityToken ─► /oauth2/apple/mobile
         verify RS256 against Apple JWKS (cached 24h), iss=https://appleid.apple.com, aud=APPLE_BUNDLE_ID|APPLE_SERVICE_ID
         find user by apple_user_id, then by email
         ├─ found → role check (ROLE_CONFLICT) → tokens
         └─ not found and no email (user hid it) → 401 EMAIL_REQUIRED (existingRole = appleUserId)

App asks the user for an email:
     /oauth2/apple/send-email-otp {email, appleUserId}     → 6-digit code by email, 10 min, apple_email_otps
     /oauth2/apple/verify-email-otp {email, appleUserId, otp} → {verified:true, verificationToken}
            verificationToken = HS512 JWT {sub: appleUserId, email, purpose: "apple_email_verification"}, 10 min

App ── verificationToken ─► /oauth2/apple/mobile → create or link the user (apple_user_id stored) → tokens
```

Without `APPLE_BUNDLE_ID` (as locally) `/apple/mobile` returns 401 "Apple Sign-In is not configured".

## 8. Verifying the stored phone / email of a logged-in user

- **Phone** (`PhoneVerificationService`):
  - `POST /api/auth/otp/send` → OTP to the stored number, using the MSG91 *verification* template.
  - `POST /api/auth/otp/verify {otp}` → re-checks that no other account verified the number meanwhile, then sets `isPhoneVerified=true`.
- **Email** (`EmailVerificationService`):
  - `POST /api/auth/email/send-verification` → 32 random bytes, base64url. The **SHA-256** is stored in `email_verification_tokens` (24 h). The raw token goes in the link `…/api/auth/email/verify?token=<raw>`.
  - Opening the link hashes the token, finds the row, sets `isEmailVerified=true`, deletes the row, and renders an HTML page that deep-links to `fixhomi://email-verified?...`.

## 9. Changing phone / email while logged in

- **Phone** (`PhoneVerificationService.sendChangeOtp` / `verifyChangeOtp`):
  - The OTP goes to the **new** number, with two limits: per number and per user.
  - Verify swaps the number and sets `isPhoneVerified=true` in one step.
  - An unverified "squatter" on that number loses it; a verified one blocks the change (400).
- **Email** (`UserService.setEmail`):
  - The new email is stored lowercase and unverified, and a verification link is sent.
  - If another active account uses it → 409.
- `PUT /api/users/profile` changes the name only. Its `phoneNumber` is used solely when the account has **no** phone yet (legacy support for app builds before 1.0.5).

## 10. Password change and reset

| Flow | Endpoint(s) | Proof | Stored where | After success |
|---|---|---|---|---|
| Change (logged in) | `POST /api/users/change-password` | current password; omitted if the account has none | — | refresh tokens revoked only if a password existed |
| Reset by link | `forgot-password` → `reset-password/validate` → `reset-password` | token from email, 1 h | `password_reset_tokens` (SHA-256) | token marked used, all refresh tokens revoked, "password changed" email |
| Reset by phone OTP | `forgot-password/phone` → `forgot-password/phone/verify` | SMS OTP | `password_reset_otps` (phone) | all refresh tokens revoked |
| Reset by email OTP | `forgot-password/email` → `forgot-password/email/verify` | email OTP | `password_reset_otps` (email) | all refresh tokens revoked |

`forgot-password` (link) always returns 200 so it doesn't reveal whether an email is registered. The phone variant returns 404 for unknown numbers.

## 11. Account deletion

| Path | Who | Proof | Type |
|---|---|---|---|
| `POST /api/users/delete-account/request-otp` → `DELETE /api/users/account {otp}` | the user (needs a **verified** phone) | SMS OTP (`delete_account_otps`, MSG91 delete template) | soft |
| `DELETE /api/users/{userId}` | the user themself, or a DB-role `ADMIN` | none | soft |
| `DELETE /api/admin/users/{userId}` | ADMIN / IT_ADMIN | none | **hard** |

**Soft delete** (`UserService.performSoftDelete`):
- revokes refresh tokens
- email → `deleted_<id>@del.local`, phone → `del_<id>` (frees both for re-registration)
- name → `[Deleted User]`, password → null, `isActive=false`

The JWT filter then rejects the user's access token with 401 `ACCOUNT_DELETED`.

**Hard delete** removes `refresh_tokens`, `trusted_devices`, `user_sessions`, `delete_account_otps` and `login_lockouts` rows, then the `users` row.

## 12. Admin user management

`AdminUserController` → `UserService` (all ADMIN/IT_ADMIN):

- **Create staff** (`createUserByAdmin`): role ∈ {ADMIN, IT_ADMIN, SUPPORT}; the same email/phone reclaim rules as register; email marked verified; password 8–100 characters (no complexity rule).
- **Enable/disable** (`updateUserStatus`): flips `is_active`. Disabling also revokes all refresh tokens. The user's next request with their access token gets 401 `ACCOUNT_DELETED`.
- **List** (`listUsers`): USER/SERVICE_PROVIDER only, with search by name/email/phone, newest first.
- **Get by id**: any role.
- **Hard delete**: see §11.

## 13. Notifications

`EmailServiceConfig` / `SmsServiceConfig` create exactly one implementation of each interface at startup:

| Property (env var) | Value | Bean |
|---|---|---|
| `fixhomi.notification.sms.provider` (`SMS_PROVIDER`) | `msg91` | `Msg91SmsService`: POST `https://control.msg91.com/api/v5/flow` with a template id |
| | anything else | `StubSmsService`: prints `OTP CODE: …` to the console |
| `fixhomi.notification.email.provider` (`EMAIL_PROVIDER`) | `brevo` | `BrevoEmailService`: POST `https://api.brevo.com/v3/smtp/email` |
| | anything else | `StubEmailService`: prints the email type, `TOKEN:` and URLs to the console |

MSG91 uses three templates: the default (`MSG91_TEMPLATE_ID`) for login/signup/reset, `MSG91_VERIFICATION_TEMPLATE_ID` for phone verify/change, and `MSG91_DELETE_TEMPLATE_ID` for account deletion.

## 14. Scheduled housekeeping

`@EnableScheduling` on `AuthServiceApplication` enables these `@Scheduled` jobs:

| Every | Job | Location |
|---|---|---|
| 10 min | delete expired OTP rows | `OtpLoginService:386`, `PhoneSignupService:240`, `PhoneVerificationService:324`, `PasswordResetService:436`, `UserService:654` |
| 30 min | delete expired / old revoked refresh tokens | `RefreshTokenService:210` |
| 30 min | delete stale login lockouts | `AuthService:365` |
| 30 min | clear all rate-limit buckets | `RateLimitingFilter:219` |
| 1 h | delete expired email-verification tokens | `EmailVerificationService:219` |

Next: [06-data-model.md](06-data-model.md)
