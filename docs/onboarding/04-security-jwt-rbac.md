# 04 · Security, JWT and RBAC

This is the most important document for working on roles. Everything here is taken from the code (file:line references are relative to `src/main/java/com/fixhomi/auth/`), and the runtime behaviour was checked against a running instance.

---

## 1. The request pipeline

A request passes through these layers in this order. The order was confirmed at runtime: anonymous requests to protected paths never reach the rate limiter.

```
Tomcat
 │
 ├─ springSecurityFilterChain   (Spring Security, order -100)
 │    ├─ CorsFilter                         SecurityConfig.corsConfigurationSource()
 │    ├─ HeaderWriterFilter                 X-Frame-Options: DENY, nosniff, HSTS
 │    ├─ OAuth2AuthorizationRequestRedirectFilter / OAuth2LoginAuthenticationFilter
 │    │                                     only for /oauth2/authorize/* and /oauth2/callback/*
 │    ├─ JwtAuthenticationFilter            ← our filter: Bearer token → SecurityContext
 │    ├─ ExceptionTranslationFilter         turns "not allowed" into the 302 described in §4
 │    └─ AuthorizationFilter                applies the rules in SecurityConfig.authorizeHttpRequests
 │
 ├─ RateLimitingFilter          (@Component @Order(1))  per-IP Bucket4j buckets
 │
 └─ DispatcherServlet
      ├─ @PreAuthorize check (method security, AdminUserController only)
      ├─ @Valid DTO validation
      ├─ Controller → Service → Repository
      └─ GlobalExceptionHandler on any exception
```

`SecurityConfig` (config/SecurityConfig.java):

| Setting | Value | Line |
|---|---|---|
| CSRF | disabled (stateless JWT API) | 55 |
| Sessions | `STATELESS`; no `JSESSIONID` is used for auth | 71–73 |
| Headers | `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, HSTS 1 year + subdomains | 61–68 |
| Public paths | the `permitAll()` list | 78–110 |
| Admin paths | `/api/admin/**` → `hasAnyRole("ADMIN", "IT_ADMIN")` | 113 |
| Everything else | `.anyRequest().authenticated()` | 116 |
| OAuth2 browser login | `/oauth2/authorize/*` → `/oauth2/callback/*` | 120–129 |
| Our JWT filter | `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)` | 132 |
| Method security | `@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)`; `@PreAuthorize` is on by default | 33 |
| Password hashing | `BCryptPasswordEncoder(12)` | 142 |
| CORS | origins from `ALLOWED_ORIGINS` (`*` is rejected), credentials allowed | 165–196 |

---

## 2. The JWT

### 2.1 What a token looks like

This is a real access token issued by a local instance, decoded:

```
Header:  {"alg":"HS512"}
Payload: {
  "role": "USER",
  "tokenType": "ACCESS",
  "userId": 1,
  "sub": "tej.user@example.com",
  "iat": 1790943068,
  "exp": 1791029468,
  "iss": "fixhomi-auth-service"
}
```

It is built in `JwtService.generateAccessToken` (security/JwtService.java:43–60):

| Claim | Source | Notes |
|---|---|---|
| `userId` | `users.id` | **The identity.** Node uses it as the MongoDB `_id`. |
| `role` | `users.role` at the moment of issue | `USER`, `SERVICE_PROVIDER`, `ADMIN`, `SUPPORT` or `IT_ADMIN` |
| `tokenType` | always `"ACCESS"` | Not checked by any verifier |
| `sub` | the email | **absent** for phone-only users (email is null) |
| `iat` / `exp` | now / now + `jwt.expiration.ms` (86 400 000 ms = 24 h) | |
| `iss` | `jwt.issuer` = `fixhomi-auth-service` | Not checked by any verifier |

- **Algorithm:** HS512, a symmetric HMAC. The key is `Keys.hmacShaKeyFor(JWT_SECRET.getBytes(UTF_8))` (JwtService.java:186–189). The secret string's raw bytes are the key; it is **not** base64-decoded. HS512 needs ≥ 64 bytes.
- **Symmetric means shared:** the Node backend verifies tokens with the **same** `JWT_SECRET`. If the two differ, every Node request returns 401 while Java keeps working ([07](07-how-other-services-use-auth.md)).
- There is no `jti`, no audience, and no session id in the token.

### 2.2 Refresh tokens

| Property | Value | Where |
|---|---|---|
| Format | `UUID` + `UUID` without dashes (68 chars), opaque | `RefreshTokenService.java:202–204` |
| Storage | `refresh_tokens` table, one row per login, stored as-is | `entity/RefreshToken.java` |
| Lifetime | 60 days (`jwt.refresh-token.expiration.days`) | `application.yaml:75` |
| Rotation | every `/api/auth/refresh` revokes the old row and links it to the new one (`rotated_at`, `replaced_by_token`) | `RefreshTokenService.java:79–122` |
| Grace window | replaying a just-rotated token within 45 s returns the **same** successor instead of 401 (for concurrent refreshes from the mobile app) | `RefreshTokenService.java:128–141`, `jwt.refresh-token.rotation-grace-seconds` |
| Revoked by | logout (one token); password change, any password reset, admin disable, soft delete (all of the user's tokens) | `revokeToken` / `revokeAllUserTokens` |

### 2.3 The access-token lifecycle as clients see it

```
login ─► accessToken (24h) + refreshToken (60d)
          │
          ├─ every API call:  Authorization: Bearer <accessToken>
          │
          ├─ app sees expiry within 5 min ─► POST /api/auth/refresh {refreshToken}
          │                                   ◄─ new accessToken + NEW refreshToken (old one revoked)
          │
          └─ POST /api/auth/logout {refreshToken}   (refresh token revoked; access token still valid until exp)
```

---

## 3. `JwtAuthenticationFilter`, line by line

`security/JwtAuthenticationFilter.java:45–107`. It runs on **every** request, public paths included.

```java
String jwt = extractJwtFromRequest(request);            // "Authorization: Bearer <jwt>" (exact prefix)
if (jwt != null && jwtService.validateToken(jwt)) {      // signature (HS512 + JWT_SECRET) and exp only
    Long userId = jwtService.getUserIdFromToken(jwt);
    Optional<User> userOpt = (userId != null)
        ? userRepository.findById(userId)                // normal path: one PK lookup per request
        : userRepository.findByEmail(sub);               // legacy tokens without userId

    if (userOpt.isEmpty())        → 401 {"message":"Account no longer exists.","code":"ACCOUNT_DELETED"}; stop
    if (!user.getIsActive())      → 401 {"message":"Account has been deactivated.","code":"ACCOUNT_DELETED"}; stop

    String role = jwtService.getRoleFromToken(jwt).name();             // ← role from the TOKEN, not the DB
    var auth = new UsernamePasswordAuthenticationToken(
                   String.valueOf(user.getId()),                       // principal = "<userId>"
                   null,
                   List.of(new SimpleGrantedAuthority("ROLE_" + role)) // exactly one authority
               );
    SecurityContextHolder.getContext().setAuthentication(auth);
}
// invalid / expired / missing token: nothing is set, the request continues as anonymous
filterChain.doFilter(request, response);
```

What follows from this:

1. **Identity** = `authentication.getName()` = the user id as a String. Controllers do `Long.valueOf(auth.getName())`.
2. **Authority** = `ROLE_<role from JWT>`, e.g. `ROLE_SUPPORT`. That is what `hasRole('SUPPORT')` / `hasAnyRole(...)` match against. Spring adds the `ROLE_` prefix in those expressions automatically.
3. **`is_active` is checked live on every request; `role` is not.** Disabling a user locks them out immediately. Changing a user's role in the DB takes effect only when they get a new access token (refresh or login), up to 24 h later.
4. **Not checked here:** whether the refresh token or session was revoked. After logout or a password change, the old access token keeps working until `exp`.
5. **A stale token can break public endpoints.** If a client sends a token for a deleted user to `/api/auth/login`, the filter answers 401 `ACCOUNT_DELETED` before the controller runs. This is why the Postman collection sets public requests to *No Auth*.

---

## 4. What a denied request looks like

There is no custom `AuthenticationEntryPoint` or `AccessDeniedHandler`, and `oauth2Login()` is enabled. Spring Security therefore uses the OAuth2 login entry point for both cases. Observed on a running instance:

| Situation | Response |
|---|---|
| No token / invalid / expired token on a protected path | **302** `Location: /oauth2/authorize/google` |
| Same, with header `X-Requested-With: XMLHttpRequest` | **302** `Location: /login` |
| Valid token, wrong role (e.g. SUPPORT → `/api/admin/users/list`) | **302** `Location: /oauth2/authorize/google` |
| Valid token, user deleted or deactivated | **401** `{"code":"ACCOUNT_DELETED", ...}` (from our filter) |
| Request reaches the controller and `@PreAuthorize` throws `AccessDeniedException` | 403 `ErrorResponse` "Access Denied" (via `GlobalExceptionHandler`). The URL rule normally denies first, so in practice the 302 above wins for `/api/admin/**` |

A browser or Postman follows the 302 to Google's sign-in page. The Postman requests in folder 10 turn off *follow redirects* so you can see the 302. The mobile app treats any non-2xx as an error and refreshes on 401 ([07](07-how-other-services-use-auth.md)).

---

## 5. RBAC

### 5.1 Roles

`entity/Role.java`: `USER`, `SERVICE_PROVIDER`, `ADMIN`, `SUPPORT`, `IT_ADMIN`. Stored as a string in `users.role` (`@Enumerated(EnumType.STRING)`, length 20, not null).

### 5.2 How a user gets each role

| Entry point | Roles it can produce | Code |
|---|---|---|
| `POST /api/auth/register` | `USER`, `SERVICE_PROVIDER` (anything else → 403 *Invalid Role*) | `AuthService.java:137` |
| `POST /api/auth/signup/phone/verify` | always `USER` | `PhoneSignupService.java:193` |
| `POST /api/auth/oauth2/google/mobile` | requested `USER`/`SERVICE_PROVIDER`; anything else becomes `USER` | `GoogleAuthService.java:141–153` |
| `POST /api/auth/oauth2/apple/mobile` | same rule as Google | `AppleAuthService.java:179–191` |
| Browser Google OAuth2 (new user) | always `USER` | `OAuth2AuthenticationSuccessHandler.java:122` |
| `POST /api/admin/users` (by ADMIN/IT_ADMIN) | `ADMIN`, `IT_ADMIN`, `SUPPORT` only | `UserService.java:399` |
| Local dev seeder | one `ADMIN` | `config/LocalDevDataSeeder.java:63` |
| Changing the role of an existing user | **no endpoint**; only a direct DB update | — |

### 5.3 Every place a role is checked

These are all of them, found by grepping for `hasRole`, `hasAnyRole`, `@PreAuthorize`, `Role.<X>` and `"ROLE_"`:

| # | Location | Check | Effect |
|---|---|---|---|
| 1 | `config/SecurityConfig.java:113` | `/api/admin/**` → `hasAnyRole("ADMIN","IT_ADMIN")` | URL-level gate for all admin endpoints (JWT role) |
| 2 | `controller/AdminUserController.java:38, 61, 83, 102, 132` | `@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")` | Method-level gate on each of the 5 admin methods (JWT role) |
| 3 | `security/JwtAuthenticationFilter.java:87` | builds `ROLE_<role>` | Turns the JWT claim into the Spring authority |
| 4 | `service/UserService.java:568–573` | `isUserAuthorizedForDeletion`: self, or `currentUser.getRole() == Role.ADMIN` | `DELETE /api/users/{userId}`. Uses the **DB** role; IT_ADMIN is not included |
| 5 | `service/AuthService.java:137` | register allow-list | USER / SERVICE_PROVIDER only |
| 6 | `service/UserService.java:399` | admin-create allow-list | ADMIN / IT_ADMIN / SUPPORT only |
| 7 | `service/UserService.java:332–339` | `listUsers` role filter | Only USER / SERVICE_PROVIDER can be listed |
| 8 | `service/GoogleAuthService.java:141–196`, `service/AppleAuthService.java:179–258` | requested role ∈ {USER, SERVICE_PROVIDER}; existing user's role must equal the requested role, otherwise `ROLE_CONFLICT` | Social sign-in |
| 9 | `service/PhoneSignupService.java:193`, `security/OAuth2AuthenticationSuccessHandler.java:122` | assign `USER` | New-account creation |

`@Secured` and `@RolesAllowed` are enabled but not used anywhere.

### 5.4 Permission matrix (current behaviour)

✅ allowed · ❌ denied (302) · **self** = only on the caller's own account

| Endpoint group | Anonymous | USER | SERVICE_PROVIDER | SUPPORT | ADMIN | IT_ADMIN |
|---|---|---|---|---|---|---|
| Public auth endpoints (register, logins, OTP, signup, refresh, logout, forgot/reset, social, health) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Self-register with this role | — | ✅ | ✅ | ❌ 403 | ❌ 403 | ❌ 403 |
| Mobile Google/Apple sign-in into an existing account of this role | — | ✅ | ✅ | ❌ `ROLE_CONFLICT` | ❌ `ROLE_CONFLICT` | ❌ `ROLE_CONFLICT` |
| `/api/users/*` (me, profile, email, phone change, password, delete own) | ❌ | self | self | self | self | self |
| `DELETE /api/users/{userId}` | ❌ | self | self | self | **any user** | self |
| `/api/auth/sessions*`, `/api/auth/devices/trust*`, `/api/auth/validate`, `/api/token/*` | ❌ | self | self | self | self | self |
| `/api/auth/otp/*`, `/api/auth/email/send-verification` | ❌ | self | self | self | self | self |
| `/api/admin/users/**` (create staff, list, get, enable/disable, hard delete) | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |

### 5.5 The SUPPORT role today

These are facts about the current code. Each was confirmed with the Postman collection (folders 09 and 10).

- **Defined:** `Role.SUPPORT` (`entity/Role.java`).
- **Created by:** an ADMIN or IT_ADMIN calling `POST /api/admin/users` with `"role":"SUPPORT"`. In production this goes through the Node admin panel proxy (`noefi/fixhomi-backend/controllers/adminManagementController.js`).
- **Can log in with:** email + password, phone + password (if a phone was set), email OTP, phone OTP, refresh, and the password-reset flows. None of these check the role.
- **Cannot log in with:** mobile Google or Apple. Those coerce the requested role to USER/SERVICE_PROVIDER and then report `ROLE_CONFLICT`.
- **Token:** `{"role":"SUPPORT", ...}` → Spring authority `ROLE_SUPPORT`.
- **Can access:** exactly what any authenticated user can, on their own account only.
- **Cannot access:** anything under `/api/admin/**` (302), or other users' data.
- **Not listed by** `GET /api/admin/users/list`, because staff roles are filtered out (`UserService.java:332–339`). An admin can still fetch a SUPPORT user by id.
- **No Java code** checks for `SUPPORT` except the admin-create allow-list.
- **In Node:** `adminLogin` accepts SUPPORT, but `requireAdmin` only allows `ADMIN`/`IT_ADMIN` (`noefi/fixhomi-backend/middlewares/adminAuthMiddleware.js`). See [07](07-how-other-services-use-auth.md).

### 5.6 IT_ADMIN vs ADMIN

They are identical everywhere except check #4: only `ADMIN` can soft-delete *another* user via `DELETE /api/users/{userId}`. Both can create other staff, including more ADMINs, and both can hard-delete via `/api/admin/users/{id}`.

---

## 6. Rate limiting

`config/RateLimitingFilter.java`. Bucket4j, in memory, one bucket per client IP per category. All buckets are cleared every 30 minutes.

| Bucket | Capacity | Applies when the path contains… (checked in this order) |
|---|---|---|
| **otp** | 5 / min | `/otp`, `send-otp`, `/forgot-password`, `/send-verification`, `/resend` |
| **auth** | 10 / min | `/login`, `/register`, `/oauth2/google`, `/token/validate`, `/refresh` |
| **general** | 100 / min | anything else |

- **GET requests are not limited** unless the path contains `/verify` (e.g. `GET /api/auth/email/verify`).
- Client IP is `request.getRemoteAddr()`. `X-Forwarded-For` is consulted only when the remote address is private/loopback (i.e. behind Render's proxy).
- Over the limit → `429 {"status":429,"error":"Too Many Requests","message":"...","path":"..."}`.
- Disable locally with `RATE_LIMIT_ENABLED=false`.

There are separate **per-identifier limits inside the services**, independent of IP:

| Limit | Value | Where |
|---|---|---|
| OTP sends per phone/email | 3 per 5 min | `fixhomi.verification.otp.rate-limit-*` in yaml; each OTP service |
| OTP verification attempts | 3 per OTP | `fixhomi.verification.otp.max-attempts` |
| OTP expiry | 5 min (Apple email OTP: 10 min) | `fixhomi.verification.otp.expiration-minutes` |
| Password login lockout | 5 failures → locked 15 min, per identifier and per user | `AuthService.java:40–44`, table `login_lockouts` |
| Email verification resend | 1 per 5 min | `fixhomi.verification.email.rate-limit-minutes` |
| Password reset link | 1 per 5 min (silently ignored) | `PasswordResetService` |

---

## 7. Secrets at rest

| Secret | Stored as | Code |
|---|---|---|
| Passwords | BCrypt, cost 12 (60-char hash) | `SecurityConfig.java:142` |
| Password-reset link tokens | SHA-256 hex | `TokenHasher`, `PasswordResetService.java:121` |
| Email-verification link tokens | SHA-256 hex | `TokenHasher`, `EmailVerificationService.java:133` |
| OTP codes | plaintext, short-lived (documented choice in `TokenHasher` Javadoc) | OTP entities |
| Refresh tokens | plaintext | `RefreshToken.token` |
| JWT signing key | env var `JWT_SECRET` only | `application.yaml:69` |

OTPs are generated with `SecureRandom` and compared with `MessageDigest.isEqual` (constant time) in the login and signup flows.

Next: [05-auth-flows.md](05-auth-flows.md)
