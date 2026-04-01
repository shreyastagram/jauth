# Security Audit — FixHomi Auth Service

> **Note on service consumers:**
> This auth service is consumed by two clients:
> - **React Native mobile app** — uses this service for OTP login,
>   email/phone verification, Google/Apple OAuth, registration,
>   password reset, and session management
> - **Node.js backend** — uses this service primarily for JWT validation
>   (GET /api/token/validate) and user identity resolution. Both
>   services share the same JWT_SECRET.
> Any security issue in this service has direct impact on both consumers.

## Architecture

**Layered MVC.** Controllers handle HTTP and delegate to `@Service` classes for business logic, which call `@Repository` interfaces (Spring Data JPA) for data access, with `@Entity` classes mapping to PostgreSQL tables. No hexagonal/ports-and-adapters separation — straightforward Spring Boot layered architecture.

## Threat Model

**Assets being protected:** User credentials (password hashes), user PII (email, phone, name), authentication tokens (JWT access, refresh, OTP, reset/verification tokens), user sessions, and the ability to authenticate as any user to downstream services (React Native app, Node.js backend).

**Assumed attackers:**
1. **External (zero-access):** Network access only. Goal: take over accounts, enumerate users, disrupt service. Most common threat.
2. **Insider / DB access:** Has direct PostgreSQL read or read-write access via compromised credentials, exposed backup, misconfigured firewall, or SQL injection in a co-located service. Goal: mass account takeover, impersonation, data exfiltration.
3. **Compromised dependency / supply chain:** A malicious library or stolen environment variable (e.g., `JWT_SECRET`). Goal: forge tokens, bypass all auth.

**What a successful attack looks like:**
- External: log in as another user, reset their password, enumerate registered accounts, or DOS the service.
- Insider/DB: read all OTPs and tokens to impersonate anyone, create admin accounts, exfiltrate all user PII.
- Compromised secret: forge JWTs for any user/role; both this service and the Node.js backend would trust them (mitigated by the per-request DB active-user check in `JwtAuthenticationFilter`).

**Out of scope:** Physical server access, attacks on Render.com infrastructure, mobile app binary reverse engineering, social engineering of end users, attacks on Brevo/MSG91 provider APIs.

## Summary

**Original findings (code review):**

| Severity | Count |
|----------|-------|
| CRITICAL | 1 |
| HIGH | 4 |
| MEDIUM | 5 |
| LOW / INFORMATIONAL | 5 |

**Additional findings from attack surface analysis:**

| Severity | External (zero-access) | Insider (DB access) |
|----------|----------------------|---------------------|
| CRITICAL | — | 2 (OTP exposure, ADMIN creation via DB) |
| HIGH | 1 (Apple nonce — already H4) | 4 (token theft x2, un-revoke tokens, JWT_SECRET role escalation) |
| MEDIUM | 3 (OTP brute-force window, credential stuffing, OTP counter reset) | 3 (PII x2, no audit trail) |
| LOW | 2 (DoS, headers) | 1 (backup encryption unknown) |

Note: Many items in the attack surface sections cross-reference the original findings (C1, H1, H3, H4) rather than introducing new vulnerabilities. The genuinely **new** issues not covered in the original findings are:
- Insider #8: Direct ADMIN account creation via DB write
- Insider #10: Un-revoking refresh tokens via DB write
- Insider #11: No audit trail for security events
- Insider #13: JWT role claim trusted over DB role — forged JWT can escalate privileges
- External #2: OTP attempt counter can be reset by requesting new OTP
- External #3: No cross-account credential stuffing detection

---

## Findings

### CRITICAL

#### C1. OTPs stored in plaintext in the database

**Files:** All OTP entities — `PhoneOtp.java`, `EmailOtp.java`, `PasswordResetOtp.java`, `DeleteAccountOtp.java`

**What:** Every OTP (phone login, email login, password reset, account deletion) is stored as a plaintext 6-digit string in the `otp` column. Anyone with database read access (DB admin, SQL injection in another service sharing the DB, backup exposure) can read OTPs and use them before the legitimate user.

**Verification in code:** `OtpLoginService.java:131` — `new PhoneOtp(user.getId(), storedPhone, otp, expiresAt)` stores the raw OTP. Comparison at line 178 uses `MessageDigest.isEqual()` on the raw bytes — this is constant-time comparison of *plaintext*, not a hash.

**Impact:** An attacker with database read access can bypass OTP verification for any user — login as anyone, reset any password, delete any account.

**Fix:** Hash OTPs with SHA-256 before storing. On verification, hash the user-supplied OTP and compare hashes. OTPs are short-lived and attempt-limited, so a fast hash is acceptable (no need for bcrypt). Apply the same pattern to `PasswordResetToken.token` and `EmailVerificationToken.token` (see H1).

---

### HIGH

#### H1. Refresh tokens and reset tokens stored in plaintext

**Files:** `RefreshToken.java:25`, `PasswordResetToken.java:26`, `EmailVerificationToken.java:26`

**What:** Refresh tokens (UUID-based), password reset tokens (Base64 random), and email verification tokens are all stored as plaintext in the database. The refresh token is a long-lived bearer credential (7 days).

**Impact:** Database read access → steal any user's refresh token → exchange for access tokens via `/api/auth/refresh`. Password reset tokens → reset any user's password.

**Fix:** Store `SHA-256(token)` in the database. On lookup, hash the incoming token and query by hash. The token value is only ever sent to the user; the DB never needs the original.

---

#### H2. Password change does not invalidate existing sessions/tokens

**File:** `UserService.java:89-113` — `changePassword()` method

**What:** After a successful password change, the method saves the new hash and returns — but does **not** call `refreshTokenService.revokeAllUserTokens(user.getId())`. Contrast this with the password *reset* flow (`PasswordResetService.java:153`) which **does** revoke all refresh tokens.

**Impact:** If a user's account is compromised and they change their password, the attacker's existing refresh tokens remain valid for up to 7 days. The attacker can continue refreshing access tokens.

**Fix:** Add `refreshTokenService.revokeAllUserTokens(user.getId())` after the password is saved in `changePassword()`. Optionally also revoke all sessions.

---

#### H3. User enumeration via phone OTP login and phone password reset endpoints

**Files:**
- `OtpLoginController.java:93` — returns 404 with `"USER_NOT_FOUND"` and message `"No account found with this phone number. Please sign up first."` when phone doesn't exist
- `OtpLoginController.java:224` — same for email OTP login
- `PasswordResetService.java:188-189` — `requestPasswordResetOtp()` throws `AuthenticationException("No account found with this phone number.")` which maps to 404
- `VerificationController.java:400` — surfaces the 404 to the client

**What:** These endpoints reveal whether a phone number or email is registered. Contrast with the email-based forgot-password flow (`PasswordResetService.java:88-103`) which correctly always returns 200 to prevent enumeration.

**Impact:** An attacker can enumerate which phone numbers and emails have accounts, enabling targeted phishing or credential stuffing.

**Fix:** For phone OTP login and phone password reset, return a generic 200 response regardless of whether the phone exists. Only return errors for rate limiting and validation failures.

---

#### H4. Apple Sign-In nonce not validated — replay attack possible

**File:** `AppleAuthService.java:293-360` — `verifyAppleIdentityToken()`

**What:** The Apple identity token verification validates issuer, audience, expiry, and signature — but does **not** validate the `nonce` claim. Apple's security documentation requires that the server generate a nonce, send it to the client, and verify it matches the claim in the returned identity token. Without nonce validation, an attacker who intercepts an Apple identity token can replay it against this endpoint.

**Impact:** A stolen (but not expired) Apple identity token can be replayed to log in as that user. Apple tokens typically have a 5-10 minute validity window.

**Fix:** Generate a server-side nonce (stored temporarily, e.g., in Redis or DB), pass it to the mobile client, and require it in `AppleMobileAuthRequest`. On verification, extract the `nonce` claim from the Apple JWT and compare with the stored value using constant-time comparison. Alternatively, use the `authorizationCode` for server-to-server validation which is single-use.

---

### MEDIUM

#### M1. OTP attempt limit check is not atomic — race condition

**Files:** `OtpLoginService.java:169-176`, `PhoneVerificationService.java:143-149`, `PasswordResetService.java:247-253`

**What:** The pattern is: read OTP → increment attempts → check if over limit → save. This is a read-modify-write without database-level locking. Two concurrent requests with the same OTP can both read `attempts=2`, both increment to 3, both pass the `> maxAttempts` check, and both attempt verification.

**Impact:** An attacker can send multiple verification requests in parallel to get more than the intended 3 attempts, increasing brute-force success rate from ~0.03% to potentially ~0.06-0.09% for a 6-digit OTP. Not catastrophic due to the small OTP space, but it weakens the limit.

**Fix:** Use `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository query that fetches the OTP, or use an `UPDATE ... SET attempts = attempts + 1 WHERE attempts < maxAttempts` pattern at the database level.

---

#### M2. Rate limiting is in-memory only — lost on restart, bypassable by IP rotation

**File:** `RateLimitingFilter.java:39-41` — buckets stored in `ConcurrentHashMap`

**What:** Rate limit state is stored in JVM memory (`ConcurrentHashMap`). On application restart (Render cold starts, deploys), all rate limit state is lost. Additionally, an attacker can bypass per-IP limits by rotating IPs (VPN, proxies, botnets).

**Mitigating factors:** The OTP services have their own per-phone/per-email rate limiting backed by database queries (e.g., `phoneOtpRepository.countRecentOtpRequests()`), which survives restarts.

**Impact:** The IP-based rate limiter can be fully bypassed by restarting the service or rotating IPs. Critical endpoints like OTP send are partially protected by the service-level DB rate limiting, but login/register have no persistent rate limiting.

**Fix:** For the application's current scale, this is acceptable if the service-level DB rate limits cover the most sensitive operations. For stronger protection, consider moving to Redis-backed rate limiting (e.g., `bucket4j-redis`).

---

#### M3. `hibernate.ddl-auto: update` in production

**File:** `application-prod.yaml:45` — `ddl-auto: update`

**What:** Production Hibernate is set to `update`, which means Hibernate will automatically modify the database schema on startup. The comment says "Temporary unblock: allow missing OTP tables to be created automatically. Revert to 'validate' after schema migration is applied."

**Impact:** Schema changes can silently alter or drop column constraints. A bug in entity definitions could widen columns, remove unique constraints, or corrupt data. Hibernate's `update` does not handle all migration scenarios safely (e.g., it won't shrink columns or add NOT NULL to existing data).

**Fix:** Change to `validate` and manage schema changes through a migration tool (Flyway or Liquibase).

---

#### M4. Brevo email service logs verification tokens and links in production

**File:** `BrevoEmailService.java:42-48`

**What:** The `sendEmailVerification()` method unconditionally logs the full verification token and verification URL:
```java
logger.info("║  Token: {}", verificationToken);
logger.info("║  Link: {}", verificationUrl);
```
This is labeled "DEV MODE" but has no conditional check — it runs in production too.

**Impact:** Anyone with access to production logs can capture email verification links and verify any user's email address.

**Fix:** Wrap in a profile check (`if (isDevProfile)`) or remove entirely. Never log secrets/tokens at `INFO` level in production.

---

#### M5. Email verification HTML reflects user email without encoding in JavaScript context

**File:** `VerificationController.java:133,225`

**What:** The `buildSuccessHtml()` method uses `String.formatted(email, deepLink, deepLink)` to inject the verified email into an HTML template. The email is HTML-escaped in the `<span class="email">%s</span>` context (it goes through `%s` into an HTML body). However, in the JavaScript context at line 225:
```java
window.location.href = '%s';
```
The `deepLink` variable includes the email as a URL parameter: `fixhomi://email-verified?email=` + email + `&status=success`. If the email contains a single quote (technically valid per RFC 5321), it could break out of the JS string.

**Impact:** Limited — email addresses with single quotes are rare and the page is a simple redirect. The email comes from the verified token in the DB (not direct user input to this endpoint), so the attack surface is narrow.

**Fix:** URL-encode the email when constructing the `deepLink` string, similar to how `buildErrorHtml()` uses `URLEncoder.encode()` for the error message.

---

### LOW / INFORMATIONAL

#### L1. H2 console enabled in default profile

**File:** `application.yaml:19` — `h2.console.enabled: true`

**What:** The H2 database console is enabled at `/h2-console` in the default Spring profile. Production uses `application-prod.yaml` which disables it (`enabled: false`).

**Current risk:** Low — production uses the `prod` profile. But if someone accidentally deploys with the default profile, the H2 console would be accessible (though it connects to an in-memory DB, not PostgreSQL).

**Recommendation:** The H2 console is not listed in SecurityConfig's `permitAll()` paths, so Spring Security should block unauthenticated access. Still, consider adding it explicitly to the denied paths.

---

#### L2. Swagger/OpenAPI disabled in production — correctly configured

**File:** `application-prod.yaml:96-99` — `springdoc.api-docs.enabled: false`, `swagger-ui.enabled: false`

**What:** Correctly disabled in production. No issue.

---

#### L3. Token passed in URL query parameter for email verification and password reset

**Files:**
- `EmailVerificationService.java:96` — `baseUrl + "/api/auth/email/verify?token=" + token`
- `PasswordResetService.java:122-123` — `frontendBaseUrl + "/reset-password?token=" + token`

**What:** Verification and reset tokens are passed as URL query parameters. These can appear in browser history, server access logs, Referer headers, and CDN/proxy logs.

**Mitigating factors:** These are single-use tokens with short expiry (1 hour for reset, 24 hours for verification). Email verification actually deletes the token after use (`EmailVerificationService.java:148`).

**Recommendation:** This is standard practice for email-based flows and acceptable. Note it as a design decision.

---

#### L4. Actuator endpoints exposure

**File:** `application.yaml:186-187` — exposes `health, info, metrics, prometheus`

**What:** `/actuator/health` and `/actuator/info` are public (`permitAll()` in SecurityConfig). `/actuator/metrics` and `/actuator/prometheus` require authentication (not in `permitAll()`).

**Current risk:** Low — health and info endpoints reveal minimal information (app name, version, Java version). Metrics are protected.

---

#### L5. Missing security headers: CSP, Referrer-Policy, Permissions-Policy

**File:** `SecurityConfig.java:61-68`

**What:** The security config sets:
- `X-Frame-Options: DENY` (good)
- `X-Content-Type-Options` (default nosniff, good)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` (good)

Missing:
- `Content-Security-Policy` — relevant for the HTML email verification page
- `Referrer-Policy` — could leak token from email verification URL in Referer header
- `Permissions-Policy`

**Impact:** Low for a primarily API service. The only HTML page is the email verification result page.

**Recommendation:** Add `Referrer-Policy: no-referrer` to prevent token leakage via Referer. Add a basic CSP for the verification HTML page.

---

## What is correctly implemented

- **JWT signing:** HS512 with configurable secret, standard JJWT library, proper claim structure. No key in source code — `JWT_SECRET` is required from environment with no default.

- **BCrypt strength 12:** `SecurityConfig.java:137` — `new BCryptPasswordEncoder(12)`. This is good; strength 12 is industry-standard.

- **Constant-time OTP comparison:** All OTP verification uses `MessageDigest.isEqual()` — `OtpLoginService.java:178`, `PhoneVerificationService.java:152`, `PasswordResetService.java:256,365`, `UserService.java:360`. This prevents timing attacks.

- **Password comparison:** Uses `passwordEncoder.matches()` (BCrypt's built-in constant-time comparison) — `AuthService.java:88`.

- **Refresh token rotation:** `RefreshTokenService.java:77-98` — old token is revoked before new one is created, within a `@Transactional` boundary. Replay of an already-rotated token will fail.

- **Active user check on every authenticated request:** `JwtAuthenticationFilter.java:57-74` — loads user from DB and checks `isActive` on every request, not just at login. Deactivated users are blocked immediately even with valid JWTs.

- **Email anti-enumeration:** `PasswordResetService.java:88-103` (email link flow) and `PasswordResetService.java:287-333` (email OTP flow) always return 200 regardless of whether the email exists.

- **Role privilege escalation prevention:** `AuthService.java:136` blocks registration with ADMIN/IT_ADMIN/SUPPORT roles. `GoogleAuthService.java:141-153` and `AppleAuthService.java:151-163` force USER/SERVICE_PROVIDER only, even if client sends a privileged role.

- **IDOR prevention on session management:** `SessionService.java:120` — `revokeSession()` verifies `session.getUser().getId().equals(user.getId())` before revoking. `UserController.java:123` — `deleteAccountById()` checks ownership or ADMIN role.

- **Trusted device ownership validation:** `SessionService.java:250-251` — `untrustDevice()` queries by `(user, deviceId)` ensuring the device belongs to the authenticated user.

- **CORS configuration:** `SecurityConfig.java:167-170` — filters out wildcard origins when `allowCredentials=true`, preventing the dangerous wildcard+credentials combination.

- **Soft delete with PII anonymization:** `UserService.java:406-428` — deleted accounts have email replaced with `deleted_{id}@del.local`, phone with `del_{id}`, name with `[Deleted User]`, password hash set to null. Refresh tokens revoked.

- **Input validation:** All DTOs use Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Pattern`, `@Size`). Password strength regex enforced on all password-accepting DTOs.

- **Scheduled cleanup jobs:** Expired tokens, OTPs, lockouts, and sessions all have `@Scheduled` cleanup tasks that run every 10-30 minutes.

- **SQL injection protection:** All repository queries use JPQL with `@Param` named parameters — no native SQL queries, no string concatenation in queries.

- **HTML escaping in email templates:** `BrevoEmailService.java:256-264` — `escapeHtml()` method properly escapes `&`, `<`, `>`, `"`, `'` in email template content.

- **Login lockout with cross-method unification:** `AuthService.java:314-325` — lockout by userId means a brute-force attempt via email also locks the phone login path for the same user.

- **OTP generation uses SecureRandom:** All OTP generation uses `java.security.SecureRandom` (not `Math.random()`).

- **Token generation uses SecureRandom:** `EmailVerificationService.java:166-168` and `PasswordResetService.java:391-394` use 32 bytes from `SecureRandom` encoded as Base64 URL-safe.

---

## Attack Surface — External Attackers (Zero Access)

Attacker has: a network connection, knowledge this is a Spring Boot auth service. No credentials, no DB access, no source code.

| # | Attack | Status | Severity |
|---|--------|--------|----------|
| 1 | Brute force — login | PROTECTED | — |
| 2 | Brute force — OTP | PARTIALLY PROTECTED | MEDIUM |
| 3 | Credential stuffing | PARTIALLY PROTECTED | MEDIUM |
| 4 | Account takeover via password reset token | PROTECTED | — |
| 5 | Account takeover via OTP reset | PARTIALLY PROTECTED | MEDIUM |
| 6 | JWT algorithm confusion / alg:none | PROTECTED | — |
| 7 | JWT from deleted/deactivated user | PROTECTED | — |
| 8 | OAuth — Google audience validation | PROTECTED | — |
| 9 | OAuth — Apple nonce replay | VULNERABLE | HIGH |
| 10 | OAuth — privilege escalation via role | PROTECTED | — |
| 11 | OAuth — double signup | PROTECTED | — |
| 12 | Session fixation / hijacking | PROTECTED | — |
| 13 | CORS abuse | PROTECTED | — |
| 14 | Mass assignment / over-posting | PROTECTED | — |
| 15 | Application-layer DoS | PARTIALLY PROTECTED | LOW |
| 16 | Information disclosure — user enumeration | VULNERABLE | HIGH |
| 17 | Information disclosure — error messages | PROTECTED | — |
| 18 | Information disclosure — actuator | PROTECTED | — |
| 19 | Token leakage via URL | PROTECTED (acceptable) | — |
| 20 | IDOR | PROTECTED | — |
| 21 | HTTP security headers | PARTIALLY PROTECTED | LOW |

---

### 1. Brute force — login (PROTECTED)

**Per-user lockout:** Yes. `AuthService.java:70` calls `checkAccountLockout(email)`, and line 80 calls `checkUnifiedLockout(user.getId())`. After 5 failed attempts (configurable via `fixhomi.auth.lockout.max-attempts`), the account is locked for 15 minutes (`AuthService.java:341`). Lockout is persisted to DB (`LoginLockout` entity), survives restarts.

**Cross-method lockout:** Yes. `AuthService.java:314-325` — `checkUnifiedLockout()` queries `LoginLockoutRepository.findActiveLocksForUser(userId, now)` which finds any active lock for the userId regardless of which identifier (email or phone) triggered it. If an attacker brute-forces via email login and gets locked, phone login for the same user is also blocked.

**Per-IP rate limiting:** Yes. `RateLimitingFilter.java:77` — auth endpoints get 10 req/min per IP. In-memory only (see M2), but supplements the DB-backed lockout.

---

### 2. Brute force — OTP (PARTIALLY PROTECTED)

**OTP search space:** 10^6 = 1,000,000 possible values (6-digit numeric, `OtpLoginService.java:356`).

**Attempt limit:** 3 attempts per OTP (`OtpLoginService.java:171` — `if (currentAttempts > maxAttempts)` where maxAttempts=3). After 3 wrong guesses, the OTP is invalidated.

**Brute-force probability per OTP:** 3/1,000,000 = 0.0003% — acceptable.

**Can the attacker request a new OTP to reset the attempt counter?** Yes. `OtpLoginService.java:124` calls `phoneOtpRepository.invalidateAllUserOtps()` before creating a new OTP. The rate limit is 3 OTP requests per 1 minute (`rateLimitMinutes` defaults to 1 in `FixhomiProperties.java:73` despite YAML saying 5). So an attacker can request 3 OTPs per minute, each with 3 attempts = 9 guesses/minute.

**Effective brute-force rate:** 9 guesses/minute = 540/hour. At 540 attempts, probability of hitting a 6-digit OTP ≈ 0.054%. Low but nonzero over sustained attack.

**Why PARTIAL:** The per-phone rate limit on OTP sending is DB-backed and survives restarts. But the attempt counter is vulnerable to the race condition in M1, and there is no per-user lockout for OTP endpoints (unlike password login). An attacker can keep requesting new OTPs indefinitely as long as they respect the per-phone rate limit.

---

### 3. Credential stuffing (PARTIALLY PROTECTED)

**Same error for wrong-password vs account-not-found:** Yes for email login — `AuthService.java:76` and `:90` both throw `"Invalid email or password"`. Same for phone login — `:232` and `:246` both throw `"Invalid phone number or password"`. This is correct.

**Cross-account detection:** No. The per-IP rate limit (`RateLimitingFilter.java`) limits to 10 login attempts per minute per IP, but an attacker with multiple IPs can test credentials across many accounts without triggering the per-user lockout (since each user only sees 1 attempt). There is no anomaly detection for login patterns across accounts.

**Why PARTIAL:** Individual account protection is good (consistent errors, per-user lockout), but there is no defense against distributed low-rate credential stuffing across many accounts from many IPs.

---

### 4. Account takeover via password reset token (PROTECTED)

**Single-use:** Yes. `PasswordResetService.java:144` — `resetToken.setUsed(true)` before password update.

**Token entropy:** 256 bits — `PasswordResetService.java:392` generates 32 random bytes via `SecureRandom`, Base64url-encoded. Unguessable.

**Rate limit on reset requests:** Yes. `PasswordResetService.java:92-95` — max 1 request per `rateLimitMinutes` (5 min) per email via `tokenRepository.countRecentRequestsByEmail()`.

**Old tokens invalidated on new request:** Yes. `PasswordResetService.java:113` — `tokenRepository.invalidateAllUserTokens(user.getId())` before creating a new token. Only the latest token works.

**Refresh tokens revoked after reset:** Yes. `PasswordResetService.java:153` — `refreshTokenService.revokeAllUserTokens(user.getId())`.

---

### 5. Account takeover via OTP password reset (PARTIALLY PROTECTED)

**Real brute-force window:** OTP expires in 5 minutes. 3 attempts allowed. So the attacker has a 5-minute window with 3 guesses per OTP.

**Can an attacker trigger a new OTP to reset the counter?** Yes, for phone-based reset: `PasswordResetService.java:201` invalidates old OTPs and creates a new one. Rate limit is 3 requests per 5 minutes (`otpRateLimitMaxRequests`, line 75). So: 3 OTPs × 3 attempts = 9 guesses per 5-minute window. Probability: 9/1,000,000 ≈ 0.0009%.

**For email-based reset:** Anti-enumeration prevents the attacker from knowing if the OTP was sent (`PasswordResetService.java:296-299` — silently returns on rate limit). This is better.

**Why PARTIAL:** Phone reset reveals whether the phone is registered (see H3), and the OTP send endpoint has no per-user lockout that would block after repeated failed OTP verifications. The probability is very low per attempt, but a patient attacker targeting a specific known phone number could sustain attempts indefinitely.

---

### 6. JWT — alg:none / algorithm confusion (PROTECTED)

`JwtService.java:58` — `signWith(getSigningKey(), SignatureAlgorithm.HS512)` explicitly specifies HS512 at signing. At validation, `JwtService.java:104-108` — `Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token)` — the JJWT library requires the signing key to be present and rejects tokens with algorithm `none` or mismatched algorithms. JJWT 0.11.5 is not vulnerable to the `alg:none` attack.

---

### 7. JWT from deleted/deactivated user (PROTECTED)

`JwtAuthenticationFilter.java:57-74` — on every authenticated request:
1. Line 57: loads user from DB by email extracted from JWT
2. Line 59: if user not found → 401 `ACCOUNT_DELETED`
3. Line 68: if `!user.getIsActive()` → 401 `ACCOUNT_DELETED`

Access tokens cannot be revoked mid-session (stateless, 24h TTL), but the per-request DB check means deactivated users are blocked within one request. This is an effective mitigation for the 24h access token window.

---

### 8. OAuth — Google audience validation (PROTECTED)

`GoogleAuthService.java:69-73` — `init()` builds a list of all valid client IDs (web, iOS, Android) and sets them as `.setAudience(clientIds)` on the `GoogleIdTokenVerifier`. The Google API Client library validates the audience claim against this list. An attacker's Google token from a different application would have a different audience and be rejected.

---

### 9. OAuth — Apple nonce replay (VULNERABLE — HIGH)

Already documented as H4. Confirmed: `AppleAuthService.java:332-337` parses and validates the Apple JWT but never extracts or checks the `nonce` claim. The `AppleMobileAuthRequest` DTO has no `nonce` field. A stolen Apple identity token can be replayed within its validity window.

---

### 10. OAuth — privilege escalation via role (PROTECTED)

`GoogleAuthService.java:141-153` — if client sends `role=ADMIN`, it logs a warning and forces `USER`. Only `USER` and `SERVICE_PROVIDER` are accepted. Same logic in `AppleAuthService.java:151-163`. Registration endpoint `AuthService.java:136` — throws `InvalidRoleException` for any role other than USER/SERVICE_PROVIDER.

---

### 11. OAuth — double signup with mode=signup (PROTECTED)

`GoogleAuthService.java:195-203` — if `mode=signup` and user already exists, throws `ALREADY_REGISTERED` error. `AppleAuthService.java:196-204` — same. No duplicate account is created.

---

### 12. Session fixation / hijacking (PROTECTED)

This is a stateless JWT service — there are no server-side HTTP sessions. `SecurityConfig.java:72` — `SessionCreationPolicy.STATELESS`. The `UserSession` entity is an application-level session tracker (not an HTTP session) and is created fresh on each login. Session IDs are database-generated auto-increment, not user-controllable. Refresh tokens are UUID-based and generated server-side.

---

### 13. CORS abuse (PROTECTED)

`SecurityConfig.java:167-176` — origins are parsed from environment, wildcards are explicitly filtered out (`!"*".equals(o)`), `allowCredentials=true`. The `setAllowedOrigins()` method in Spring does **not** accept `null` origin when credentials are enabled (Spring Security rejects it). An attacker from `evil.com` would be blocked by the browser's CORS preflight. Null origins are not in the allowed list and would be rejected.

---

### 14. Mass assignment / over-posting (PROTECTED)

**Can a user self-assign a role?** No. `UpdateProfileRequest.java` only has `fullName` and `phoneNumber` fields — no `role`, `isActive`, `isAdmin`, or `email` field. `UserService.java:117-179` — `updateProfile()` only modifies `fullName` and `phoneNumber` from the request. The email (identity) is taken from the JWT, not the request body.

**Can a user modify another user?** No. All user-facing endpoints extract the current user from the JWT (`getCurrentUserEmail()` / `Authentication.getName()`), not from a request parameter.

---

### 15. Application-layer DoS (PARTIALLY PROTECTED)

**Unauthenticated DB load:** Public endpoints like `/api/auth/login`, `/api/auth/register`, and OTP endpoints all hit the database. The per-IP rate limiter (`RateLimitingFilter.java:77`) limits auth endpoints to 10/min and OTP to 5/min per IP. But this is in-memory and per-instance — a botnet with many IPs or a restart can bypass it.

**Slow HTTP attacks:** No explicit protection (no request timeout configured in `application.yaml`). Spring Boot's default Tomcat connector has a 60-second timeout, which provides basic protection.

**Connection pool exhaustion:** `application-prod.yaml:25` — HikariCP `maximum-pool-size: 5`. The `JwtAuthenticationFilter` runs a DB query on every authenticated request (`userRepository.findByEmail`). Under sustained load, all 5 connections could be tied up by slow queries. However, the pool has `connection-timeout: 30000` (30s), so waiting requests would fail rather than queue indefinitely.

**Why PARTIAL:** Basic protection exists via rate limiting and pool timeouts, but a distributed attacker can cause degraded performance. This is normal for a single-instance service without a WAF.

---

### 16. Information disclosure — user enumeration (VULNERABLE — HIGH)

Already documented as H3. To summarize all endpoints:

| Endpoint | Leaks existence? | Evidence |
|----------|-----------------|----------|
| `POST /api/auth/login` | No | `AuthService.java:76,90` — same error for both |
| `POST /api/auth/login/phone` | No | `AuthService.java:232,246` — same error for both |
| `POST /api/auth/register` | Yes (by design) | `AuthService.java:144` — 409 if email exists. Acceptable for registration. |
| `POST /api/auth/login/phone/send-otp` | **Yes** | `OtpLoginService.java:113` — 404 `USER_NOT_FOUND` |
| `POST /api/auth/login/email/send-otp` | **Yes** | `OtpLoginService.java:248` — 404 `USER_NOT_FOUND` |
| `POST /api/auth/forgot-password` | No | `PasswordResetService.java:99-103` — silent return |
| `POST /api/auth/forgot-password/phone` | **Yes** | `PasswordResetService.java:188` — throws if not found |
| `POST /api/auth/forgot-password/email` | No | `PasswordResetService.java:301-305` — silent return |

---

### 17. Information disclosure — error messages (PROTECTED)

`GlobalExceptionHandler.java:236-251` — the catch-all `Exception` handler returns `"An unexpected error occurred. Please try again later."` and never leaks stack traces or internal messages. The specific exception handlers return controlled messages. `application.yaml:83-84` — `include-message: always` and `include-binding-errors: always` are set, but these are overridden by the `@RestControllerAdvice` which handles all exceptions before Spring's default error handling.

---

### 18. Information disclosure — actuator (PROTECTED)

`SecurityConfig.java:102-104` — only `/actuator/health` and `/actuator/info` are in `permitAll()`. `application.yaml:199-205` — info exposes: app name ("FixHomi Auth Service"), description, version ("1.0.0"), Java version. No sensitive data. `/actuator/metrics` and `/actuator/prometheus` require JWT auth.

---

### 19. Token leakage via URL (PROTECTED — acceptable)

Already documented as L3. Email verification token appears in `GET /api/auth/email/verify?token=...` and password reset token in deep link URLs. Both are single-use and short-lived. The email verification token is deleted after use (`EmailVerificationService.java:148`). The password reset token is marked used (`PasswordResetService.java:144`). Standard practice for email-based flows.

---

### 20. IDOR (PROTECTED)

- **Sessions:** `SessionService.java:120` — `revokeSession()` checks `session.getUser().getId().equals(user.getId())`.
- **Trusted devices:** `SessionService.java:250-251` — `untrustDevice()` queries `findByUserAndDeviceIdAndIsActiveTrue(user, deviceId)` — scoped to the authenticated user.
- **User deletion by ID:** `UserController.java:123` — `isUserAuthorizedForDeletion()` checks ownership or ADMIN role.
- **Admin endpoints:** `AdminUserController.java:35,58` — `@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")`.
- **Profile endpoints:** All use `getCurrentUserEmail()` from JWT, never accept userId from request.

No numeric-ID endpoint allows cross-user access without authorization check.

---

### 21. HTTP security headers (PARTIALLY PROTECTED — LOW)

**Set:**
- `X-Frame-Options: DENY` — `SecurityConfig.java:62`
- `X-Content-Type-Options: nosniff` — `SecurityConfig.java:63` (Spring default)
- `Strict-Transport-Security: max-age=31536000; includeSubDomains` — `SecurityConfig.java:64-67`

**Not set:**
- `Content-Security-Policy` — relevant only for the single HTML email verification page
- `Referrer-Policy` — could leak email verification token via Referer if user clicks a link on the verification page (no links exist except the deep link, so risk is theoretical)
- `Permissions-Policy` — not relevant for an API service

---

## Attack Surface — Insider / DB Access Attackers

Attacker has: direct PostgreSQL read or read-write access. This includes compromised DB credentials, exposed backups, misconfigured firewall on port 5432, or SQL injection in another service sharing the same Postgres instance.

| # | Attack | Status | Severity |
|---|--------|--------|----------|
| 1 | OTP exposure → bypass any OTP flow | VULNERABLE | CRITICAL |
| 2 | Refresh token theft → impersonate any user | VULNERABLE | HIGH |
| 3 | Reset/verification token theft → takeover accounts | VULNERABLE | HIGH |
| 4 | Password hash cracking | PROTECTED | — |
| 5 | PII exposure from users table | VULNERABLE (by design) | MEDIUM |
| 6 | PII exposure from sessions/devices tables | VULNERABLE (by design) | MEDIUM |
| 7 | Deleted account data recovery | PROTECTED | — |
| 8 | Schema manipulation — create ADMIN account | VULNERABLE (write access) | CRITICAL |
| 9 | Schema manipulation — reactivate banned user | PARTIALLY PROTECTED | HIGH |
| 10 | Schema manipulation — un-revoke refresh token | VULNERABLE (write access) | HIGH |
| 11 | Audit trail / tamper evidence | VULNERABLE | MEDIUM |
| 12 | Backup and data-at-rest encryption | UNKNOWN | MEDIUM |
| 13 | Cross-service blast radius via JWT_SECRET | PARTIALLY PROTECTED | HIGH |

---

### 1. OTP exposure (VULNERABLE — CRITICAL)

Cross-reference with finding C1. If the attacker reads the `phone_otps`, `email_otps`, `password_reset_otps`, or `delete_account_otps` tables, all OTP values are in plaintext in the `otp` column.

**What the attacker can do:**
- Read a pending OTP → use it before the legitimate user → log in as that user (phone/email OTP login), reset their password, or delete their account.
- Query: `SELECT otp FROM phone_otps WHERE phone_number = '9876543210' AND verified = false AND expires_at > NOW() ORDER BY created_at DESC LIMIT 1`

**File/line confirming plaintext storage:** `OtpLoginService.java:131` — `new PhoneOtp(user.getId(), storedPhone, otp, expiresAt)` stores raw OTP. Same pattern in `PasswordResetService.java:208`, `UserService.java:307`, `OtpLoginService.java:263`.

**Fix:** Hash with SHA-256 before storing. On verification, hash input and compare.

---

### 2. Refresh token theft (VULNERABLE — HIGH)

Cross-reference with finding H1. The `refresh_tokens.token` column stores the raw UUID+random string.

**What the attacker can do:**
- Pick any non-revoked, non-expired token from the table
- Call `POST /api/auth/refresh` with that token
- Receive a fresh access token + new refresh token for that user
- Query: `SELECT token FROM refresh_tokens WHERE revoked = false AND expires_at > NOW()`

**File/line:** `RefreshToken.java:25` — `private String token` stored as-is. `RefreshTokenRepository.java:28` — `findValidToken` queries by plaintext token value.

**Fix:** Store `SHA-256(token)` in the `token` column. Hash incoming token before querying.

---

### 3. Reset/verification token theft (VULNERABLE — HIGH)

**Password reset tokens:** `password_reset_tokens.token` is plaintext (64-char Base64url). Attacker can call `POST /api/auth/reset-password` with a stolen token and a new password → takeover account.

**Email verification tokens:** `email_verification_tokens.token` is plaintext. Attacker can call `GET /api/auth/email/verify?token=...` → verify any user's email.

**File/line:** `PasswordResetToken.java:26` — `private String token`. `EmailVerificationToken.java:26` — `private String token`. Both stored as-is.

**Fix:** Same as #2 — store SHA-256 hash, hash on lookup.

---

### 4. Password hash cracking (PROTECTED)

`SecurityConfig.java:137` — `new BCryptPasswordEncoder(12)`. BCrypt with cost factor 12 means ~2^12 = 4096 iterations of the Blowfish key schedule. At current hardware speeds, this yields roughly 3-5 hashes/second on a GPU. A strong password (12+ chars, mixed case, digits, special) would take centuries to crack. Even a weak 8-char password would take months.

No plaintext or weakly-hashed passwords exist anywhere in the codebase. OAuth-only users have `passwordHash = null` (`GoogleAuthService.java:269`, `AppleAuthService.java:458`).

---

### 5. PII exposure from users table (VULNERABLE by design — MEDIUM)

The `users` table stores in plaintext:
- `email` — user's email address
- `phone_number` — user's phone number (10 digits, normalized)
- `full_name` — user's full name

This is standard for most applications. There is no column-level encryption.

**What the attacker can do:** Exfiltrate all user PII with a single `SELECT * FROM users`.

**Mitigation:** This is a design trade-off. Column-level encryption would complicate queries (can't search by encrypted email without a separate hash index). For this application's scale, the priority should be protecting DB access (strong credentials, network isolation, VPN).

---

### 6. PII in sessions/devices tables (VULNERABLE by design — MEDIUM)

**`user_sessions` table stores:**
- `ip_address` (String 45) — user's IP address
- `location` (String 255) — user's location (if populated)
- `device_name`, `device_model`, `platform`, `system_version`, `app_version` — device fingerprint data

**`trusted_devices` table stores:**
- Same device metadata plus `custom_name` (user-assigned device name)

**File/line:** `UserSession.java:59-63` — `ipAddress` and `location` fields. `TrustedDevice.java:33-50` — device metadata.

**What the attacker can do:** Correlate user identity with IP addresses and device fingerprints. Could be used for targeted tracking.

---

### 7. Deleted account data recovery (PROTECTED)

`UserService.java:406-428` — `performSoftDelete()`:
- Email → `deleted_{id}@del.local` (original email destroyed)
- Phone → `del_{id}` (original phone destroyed)
- Full name → `[Deleted User]`
- Password hash → `null`
- `isActive` → `false`
- All refresh tokens revoked

The original email and phone are **overwritten**, not just flagged. They cannot be recovered from the `users` table. However, the original email may still appear in `email_verification_tokens.email`, `password_reset_tokens.email`, and `email_otps.email` until those records are cleaned up by scheduled jobs (every 10-60 minutes).

---

### 8. Create ADMIN account via DB write (VULNERABLE — CRITICAL)

If the attacker has write access to the `users` table, they can:
```sql
INSERT INTO users (email, password_hash, full_name, role, is_active, is_email_verified, is_phone_verified, created_at, updated_at)
VALUES ('admin@evil.com', '$2a$12$...', 'Evil Admin', 'ADMIN', true, true, false, NOW(), NOW());
```

The application has no integrity check on the `users` table beyond the standard JPA constraints. There is no HMAC signature on rows, no admin account whitelist, and no startup validation that admin accounts match an expected set.

**Impact:** Full administrative access to the system — can disable any user, create more admin accounts via the API.

**Fix:** This is inherent to any system with DB write access. Mitigate by: (1) strictly limiting DB write credentials, (2) monitoring for new ADMIN/IT_ADMIN accounts via an alerting query, (3) requiring MFA for admin operations (not currently implemented).

---

### 9. Reactivate banned user (PARTIALLY PROTECTED — HIGH)

If the attacker sets `UPDATE users SET is_active = true WHERE id = <banned_user_id>`:

**Does the app detect it on next request?** Yes — but in the attacker's favor. `JwtAuthenticationFilter.java:67-68` checks `user.getIsActive()` on every request. So if the banned user still has a valid JWT (issued before ban, within 24h TTL), and the attacker flips `isActive` back to `true`, the user's JWT would start working again immediately on the next request.

**What about revoked refresh tokens?** The ban flow (`UserService.java:190-191`) revokes all refresh tokens. But the attacker could also flip those: `UPDATE refresh_tokens SET revoked = false WHERE user_id = <id>`. See #10.

**Why PARTIAL:** The per-request active check is good defense against stale JWTs, but it also means DB write access can instantly un-ban someone.

---

### 10. Un-revoke refresh token (VULNERABLE — HIGH)

If the attacker executes:
```sql
UPDATE refresh_tokens SET revoked = false, expires_at = NOW() + INTERVAL '7 days' WHERE user_id = <target_id>;
```

Then calls `POST /api/auth/refresh` with the token value (which they can read from the same table — see #2), they get fresh access tokens for that user.

**File/line:** `RefreshTokenRepository.java:28-29` — `findValidToken` checks `revoked = false AND expiresAt > :now`. If the attacker modifies both columns, the token passes validation.

**Fix:** Hashing tokens (see #2) would prevent the attacker from knowing the token value to send to the API, even if they un-revoke it in the DB.

---

### 11. Audit trail / tamper evidence (VULNERABLE — MEDIUM)

**Is there a tamper-evident audit log?** No. There is no audit table for login events, password changes, admin actions, or account modifications. All evidence of these events exists only in application logs (stdout/Render logs), which are separate from the database.

**If an insider deletes rows from `login_lockouts`:** There is no record. The attacker can delete all lockout records and then brute-force a login without any lockout protection. Query: `DELETE FROM login_lockouts WHERE identifier = 'victim@email.com'`.

**Are `created_at`/`updated_at` fields modifiable by a DB writer?** Yes. These are JPA-managed (`@CreatedDate`, `@LastModifiedDate`) but have no database-level triggers or immutability constraints. An attacker with DB write access can set them to any value: `UPDATE users SET created_at = '2020-01-01' WHERE id = 1`.

**Fix:** For high-security environments, add a separate append-only audit table (or external audit log) for security-critical events. Consider database triggers with a separate audit schema that the application's DB user cannot modify.

---

### 12. Backup and data-at-rest encryption (UNKNOWN — MEDIUM)

**DB encryption:** There is no indication in the codebase that database-level encryption is configured. This depends entirely on the PostgreSQL provider (Neon/Supabase). Neon provides encryption at rest by default. Supabase also encrypts at rest.

**Secrets in database:** No. `JWT_SECRET`, `BREVO_API_KEY`, `MSG91_AUTH_KEY`, and all other secrets are stored as environment variables (`application.yaml` uses `${VAR}` syntax). None are persisted to the database.

**Backup encryption:** Unknown — depends on Neon/Supabase configuration. Not controlled by this codebase.

---

### 13. Cross-service blast radius via JWT_SECRET (PARTIALLY PROTECTED — HIGH)

**What can be forged:** If an insider extracts `JWT_SECRET` from the auth service environment, they can forge any JWT with any userId, email, and role (including ADMIN). Both the auth service and the Node.js backend accept these JWTs.

**Can a forged JWT bypass the active-user check?** Partially. The `JwtAuthenticationFilter.java:57` loads the user from DB by the email in the JWT. A forged JWT with a real user's email would pass the DB check (the user exists and is active). A forged JWT with a non-existent email would be caught at line 59-64 (user not found → 401).

**But:** The attacker doesn't need a non-existent email. They can forge a JWT with `email=existing_user@email.com` and `role=ADMIN` and the filter will set `ROLE_ADMIN` authority from the JWT (line 76-80). The DB check verifies the user exists and is active, but does **not** verify that the role in the JWT matches the role in the database.

**Impact:** An attacker with JWT_SECRET can escalate any existing user to ADMIN by forging a JWT with `role=ADMIN`. The filter trusts the JWT's role claim without cross-checking the DB.

**File/line:** `JwtAuthenticationFilter.java:76` — `String role = jwtService.getRoleFromToken(jwt).name()` reads role from JWT, not from `user.getRole()`.

**Fix:** In `JwtAuthenticationFilter`, after loading the user from DB, use `user.getRole()` instead of `jwtService.getRoleFromToken(jwt)` for the authority. This way, even a forged JWT with `role=ADMIN` would only get the user's actual DB role. This also defends against a scenario where an admin is demoted but still holds a valid JWT with the old role.
