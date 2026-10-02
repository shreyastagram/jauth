# 08 · Walkthrough: Send OTP → Login → Validate Token

This page follows **one real request at a time** through every layer of the service: HTTP, filters, controller, service, repository, database, notification, token, and back. The requests, console lines, database rows and tokens below were captured from a local instance (`./run.sh local`), not written by hand.

If you understand this page, you understand how every other endpoint works. They all use the same layers.

The scenario: an existing customer (`walk.through@example.com`, phone `+919812345678`) logs in with a phone OTP, and then a client checks the token.

```
 Mobile app / Postman                          jauth
 ─────────────────────                         ─────
 ① POST /api/auth/login/phone/send-otp ──────► create OTP row, "send" SMS
                                    ◄────────── 200 {success, maskedPhone, expiresInMinutes}
   (user reads the SMS — locally: the console)
 ② POST /api/auth/login/phone/verify  ───────► check OTP, issue JWT + refresh token
                                    ◄────────── 200 LoginResponse
 ③ GET /api/token/validate  (Bearer JWT) ────► JwtAuthenticationFilter → TokenController
                                    ◄────────── 200 {valid:true, userId, role, ...}
```

---

## Part 1: `POST /api/auth/login/phone/send-otp`

### 1.1 The request

```http
POST /api/auth/login/phone/send-otp HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{"phoneNumber": "+919812345678"}
```

No `Authorization` header. This endpoint is public.

### 1.2 Through the filter chain

| Layer | What happens for this request | Code |
|---|---|---|
| CORS / headers | no `Origin` header (not a browser), so nothing to check; security headers are added to the response | `SecurityConfig` |
| `JwtAuthenticationFilter` | no `Authorization` header → `extractJwtFromRequest` returns `null` → nothing is set; the request continues as anonymous | `security/JwtAuthenticationFilter.java:49–51` |
| `AuthorizationFilter` | `/api/auth/login/phone/send-otp` is in the `permitAll()` list → allowed | `config/SecurityConfig.java:98` |
| `RateLimitingFilter` | POST, path contains `send-otp` → **otp bucket** (5/min for this IP); one token consumed | `config/RateLimitingFilter.java:88–115` |

If the IP had already sent 5 OTP requests this minute, the rate limiter would answer **429** here and the controller would never run.

### 1.3 Controller: binding and validation

```java
// controller/OtpLoginController.java:78–111
@PostMapping("/phone/send-otp")
public ResponseEntity<?> sendPhoneLoginOtp(@Valid @RequestBody PhoneOtpLoginRequest request) {
    logger.info("Phone OTP login request for: {}", maskPhoneNumber(request.getPhoneNumber()));
    try {
        String maskedPhone = otpLoginService.sendPhoneLoginOtp(request.getPhoneNumber());
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "OTP sent successfully to " + maskedPhone,
            "maskedPhone", maskedPhone,
            "expiresInMinutes", 5));
    } catch (AuthenticationException e) {      // unknown / disabled account
        return ResponseEntity.status(404).body(Map.of("success", false, "code", "USER_NOT_FOUND", "message", e.getMessage()));
    } catch (TooManyRequestsException e) {     // per-phone limit
        return ResponseEntity.status(429).body(Map.of("success", false, "code", "TOO_MANY_REQUESTS", "message", e.getMessage()));
    } catch (Exception e) {                    // e.g. SMS provider failed
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", ...));
    }
}
```

- `@RequestBody` → Jackson turns the JSON into `PhoneOtpLoginRequest`.
- `@Valid` runs the Bean Validation annotations on the DTO **before** the method body:
  ```java
  // dto/PhoneOtpLoginRequest.java
  @NotBlank(message = "Phone number is required")
  @Pattern(regexp = "^\\+?[1-9]\\d{6,14}$", message = "Invalid phone number format")
  private String phoneNumber;
  ```
  A failure throws `MethodArgumentNotValidException` → `GlobalExceptionHandler` → **400** `{"error":"Validation Failed","validationErrors":{"phoneNumber":"Invalid phone number format"}}`.
- This controller catches its own exceptions and returns `{success, code, message}` maps. The mobile app reads `code` to choose the message to show.

Console:
```
c.f.auth.controller.OtpLoginController   : Phone OTP login request for: +919***5678
```

### 1.4 Service: the business logic

`service/OtpLoginService.java:98–145`. `@Transactional`, so everything below commits or rolls back together.

```java
phoneNumber = User.normalizePhoneNumber(phoneNumber);                 // "+919812345678" → "9812345678"

long recent = phoneOtpRepository.countRecentOtpRequests(phoneNumber,  // ① per-phone rate limit
                 LocalDateTime.now().minusMinutes(rateLimitMinutes)); //    yaml: 5 minutes
if (recent >= rateLimitMaxRequests)                                    //    yaml: 3
    throw new TooManyRequestsException("Too many OTP requests. Please wait before trying again.");

User user = findUserByPhoneFlexible(phoneNumber);                      // ② account must exist
if (user == null)        throw new AuthenticationException("No account found with this phone number. Please sign up first.");
if (!user.getIsActive()) throw new AuthenticationException("Your account has been disabled. Please contact support.");

phoneOtpRepository.invalidateAllUserOtps(user.getId(), storedPhone);   // ③ older unused codes → verified=true

String otp = generateOtp();                                            // ④ 6 digits, SecureRandom
phoneOtpRepository.save(new PhoneOtp(user.getId(), storedPhone, otp,
                                     LocalDateTime.now().plusMinutes(5)));

boolean sent = smsService.sendOtp(phoneNumber, otp);                   // ⑤ notification
if (!sent) { /* mark the row unusable */ throw new VerificationException("Failed to send OTP. Please try again."); }

return maskPhoneNumber(phoneNumber);                                   // "****5678"
```

Where the numbers come from (`application.yaml` → `fixhomi.verification.otp`): `length: 6`, `expiration-minutes: 5`, `max-attempts: 3`, `rate-limit-minutes: 5`, `rate-limit-max-requests: 3`. They are injected with `@Value`.

### 1.5 Repository and database

The two JPQL queries used above (`repository/PhoneOtpRepository.java`):

```java
@Query("SELECT COUNT(p) FROM PhoneOtp p WHERE p.phoneNumber = :phoneNumber AND p.createdAt > :since")
long countRecentOtpRequests(...);

@Modifying
@Query("UPDATE PhoneOtp p SET p.verified = true WHERE p.userId = :userId AND p.phoneNumber = :phoneNumber AND p.verified = false")
int invalidateAllUserOtps(...);
```

The row written to `phone_otps` (entity `entity/PhoneOtp.java`), read from the database right after the call:

```
ID | USER_ID | PHONE_NUMBER | OTP    | VERIFIED | ATTEMPTS | EXPIRES_AT                 | CREATED_AT
7  | 13      | 9812345678   | 652707 | FALSE    | 0        | 2026-10-02 18:12:23.351630 | 2026-10-02 18:07:23.352284
```

### 1.6 Notification

`SmsService` is an interface. `SmsServiceConfig` chose the implementation at startup from `fixhomi.notification.sms.provider`:
- `local` profile → `stub` → `StubSmsService` prints the message;
- production → `msg91` → `Msg91SmsService` POSTs `{template_id, recipients:[{mobiles:"91XXXXXXXXXX", OTP}]}` to `https://control.msg91.com/api/v5/flow`.

The service code is identical in both cases. Console in local:

```
c.f.a.s.notification.StubSmsService      : ========================================
c.f.a.s.notification.StubSmsService      :      STUB SMS SERVICE - DEV MODE
c.f.a.s.notification.StubSmsService      : ========================================
c.f.a.s.notification.StubSmsService      :  TEMPLATE: DEFAULT_LOGIN
c.f.a.s.notification.StubSmsService      :  TO: 9812345678
c.f.a.s.notification.StubSmsService      :  OTP CODE: 652707
c.f.a.s.notification.StubSmsService      :  Use code 652707 to verify
c.f.a.s.notification.StubSmsService      : ========================================
c.fixhomi.auth.service.OtpLoginService   : Login OTP sent to phone: ****5678
```

### 1.7 The response

```http
HTTP/1.1 200
Content-Type: application/json

{"success":true,"maskedPhone":"****5678","expiresInMinutes":5,"message":"OTP sent successfully to ****5678"}
```

Every outcome of this endpoint:

| Situation | Status | Body |
|---|---|---|
| Sent | 200 | as above |
| Bad phone format | 400 | `ErrorResponse` with `validationErrors.phoneNumber` |
| No account / disabled account | 404 | `{"success":false,"code":"USER_NOT_FOUND","message":"No account found with this phone number. Please sign up first."}` |
| 4th request for this phone within 5 min | 429 | `{"success":false,"code":"TOO_MANY_REQUESTS",...}` |
| 6th OTP-type request from this IP within 1 min | 429 | `{"status":429,"error":"Too Many Requests",...}` (filter) |
| SMS provider failed | 500 | `{"success":false,"message":"Failed to send OTP. Please try again."}` |

---

## Part 2: `POST /api/auth/login/phone/verify`

```http
POST /api/auth/login/phone/verify
Content-Type: application/json

{"phoneNumber": "9812345678", "otp": "652707"}
```

Filter chain: public path; rate-limit bucket **auth** (the path contains `/login`, not `send-otp`).

`service/OtpLoginService.java:151–229`, annotated **`@Transactional(noRollbackFor = VerificationException.class)`**:

```java
PhoneOtp otpEntry = phoneOtpRepository.findLatestValidOtpByPhone(phoneNumber, now)  // newest unverified & unexpired
        .orElse(null);
if (otpEntry == null)  throw new VerificationException("No pending login. Please request a new OTP.");

otpEntry.incrementAttempts();                                         // counts this try
if (otpEntry.getAttempts() > maxAttempts) { mark used; throw ... "Maximum verification attempts exceeded..." }

if (!MessageDigest.isEqual(otpEntry.getOtp().getBytes(UTF_8),         // constant-time compare
                           otpCode.getBytes(UTF_8))) {
    phoneOtpRepository.save(otpEntry);                                // persist attempts++
    throw new VerificationException("Invalid OTP. Please check and try again.");
}

otpEntry.setVerified(true);                                           // single use
User user = userRepository.findById(otpEntry.getUserId())...;
if (!user.getIsActive()) throw new AuthenticationException(...);
if (!user.getIsPhoneVerified() && otp phone == user phone) user.setIsPhoneVerified(true);   // OTP proves the phone
user.setLastLoginAt(now);

String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);    // new row in refresh_tokens
return new LoginResponse(accessToken, refreshToken.getToken(), ...);
```

**Why `noRollbackFor` matters.** A `VerificationException` is a `RuntimeException`. By default Spring rolls the transaction back when one is thrown, which would undo `attempts++`, so a wrong guess would not count. `noRollbackFor` keeps the update. In the captured run, one wrong guess (`000000`) and then the right code gave:

```
first try  → 400 {"success":false,"message":"Invalid OTP. Please check and try again.","code":"INVALID_OTP"}
second try → 200 LoginResponse

phone_otps after:  ID 7 | OTP 652707 | VERIFIED TRUE | ATTEMPTS 2
users after:       ID 13 | IS_PHONE_VERIFIED TRUE | LAST_LOGIN_AT 2026-10-02 18:07:23.678
```

The controller maps the exception message to a `code`: "expired"/"No pending" → `OTP_EXPIRED`, "Maximum" → `MAX_ATTEMPTS_EXCEEDED`, anything else → `INVALID_OTP`.

The response:

```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJyb2xlIjoiVVNFUiIsInRva2VuVHlwZSI6IkFDQ0VTUyIsInVzZXJJZCI6MTMsInN1YiI6IndhbGsudGhyb3VnaEBleGFtcGxlLmNvbSIsImlhdCI6MTc5MDk0NDY0MywiZXhwIjoxNzkxMDMxMDQzLCJpc3MiOiJmaXhob21pLWF1dGgtc2VydmljZSJ9.iKxclLhLN6BT...",
  "refreshToken": "111e9ca9-3a36-4d54-a6f1-0d746ecde94242d8e37bfeee41d3bda011a0cb19882f",
  "tokenType": "Bearer",
  "userId": 13,
  "email": "walk.through@example.com",
  "fullName": "Walk Through",
  "role": "USER",
  "expiresIn": 86400,
  "isNewUser": false,
  "phoneNumber": "9812345678",
  "isPhoneVerified": true,
  "isEmailVerified": false
}
```

### What is inside the access token

A JWT is `base64url(header).base64url(payload).signature`. Decoding the first two parts of the token above:

```json
{"alg":"HS512"}
{"role":"USER","tokenType":"ACCESS","userId":13,"sub":"walk.through@example.com",
 "iat":1790944643,"exp":1791031043,"iss":"fixhomi-auth-service"}
```

The third part is `HMAC-SHA512(header + "." + payload, JWT_SECRET)`. Anyone can **read** the payload; only someone with `JWT_SECRET` (jauth and Node) can **produce** a valid signature. Changing a single character of the payload, for example `"role":"ADMIN"`, invalidates the signature. That is what makes the `role` claim trustworthy.

Built by `JwtService.generateAccessToken` (security/JwtService.java:43–60):

```java
claims.put("userId", userId);
claims.put("role", role.name());
claims.put("tokenType", "ACCESS");
return Jwts.builder()
        .setClaims(claims).setSubject(email)
        .setIssuedAt(now).setExpiration(new Date(now.getTime() + jwtExpirationMs))   // 24h
        .setIssuer(issuer)
        .signWith(getSigningKey(), SignatureAlgorithm.HS512)
        .compact();
```

---

## Part 3: `GET /api/token/validate`

The client now uses the token.

```http
GET /api/token/validate HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJyb2xlIjoiVVNFUiIs...
```

### 3.1 `JwtAuthenticationFilter` does the real work

This happens for **every** authenticated endpoint, not just this one (`security/JwtAuthenticationFilter.java:45–107`):

1. `extractJwtFromRequest`: header starts with `"Bearer "` → take the rest.
2. `jwtService.validateToken(jwt)`: `Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt)`. This verifies the HS512 signature and `exp`. Any failure (bad signature, malformed, expired) → `false` → the request stays anonymous.
3. `userRepository.findById(13)`: the user must still exist (else 401 `ACCOUNT_DELETED`) and be active (else 401 `ACCOUNT_DELETED`).
4. Build the `Authentication`:
   - principal = `"13"`
   - authorities = `[ROLE_USER]` (role read **from the token**)
5. `SecurityContextHolder.getContext().setAuthentication(auth)`.

Then `AuthorizationFilter` sees `/api/token/validate` is not public, so it requires `authenticated()`. The context holds an authentication, so the request is allowed. The rate limiter skips it (GET).

### 3.2 Controller and service

```java
// controller/TokenController.java:34–39
@GetMapping("/validate")
public ResponseEntity<TokenValidationResponse> validateToken(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    return ResponseEntity.ok(tokenValidationService.validateToken(authHeader));
}
```

```java
// service/TokenValidationService.java:31–74
if (token.startsWith("Bearer ")) token = token.substring(7);
if (!jwtService.validateToken(token)) return TokenValidationResponse.invalid();
Claims claims = jwtService.getClaimsFromToken(token);
return new TokenValidationResponse(true,
        claims.get("userId", Long.class), claims.getSubject(),
        Role.valueOf(claims.get("role", String.class)), claims.get("tokenType", String.class),
        claims.getIssuedAt().getTime() / 1000, claims.getExpiration().getTime() / 1000);
```

### 3.3 The response

```http
HTTP/1.1 200
Content-Type: application/json

{"valid":true,"userId":13,"email":"walk.through@example.com","role":"USER","tokenType":"ACCESS","issuedAt":1790944643,"expiresAt":1791031043}
```

### 3.4 What if the token is bad?

| Token | What the filter does | Response |
|---|---|---|
| valid | sets authentication | 200 `{valid:true,...}` |
| missing / garbage / wrong signature / expired | `validateToken` → false; stays anonymous | **302** → `/oauth2/authorize/google` (Spring's entry point; see [04 §4](04-security-jwt-rbac.md#4-what-a-denied-request-looks-like)) |
| valid, but user deleted or disabled since | writes the response itself | 401 `{"code":"ACCOUNT_DELETED"}` |

So in practice this endpoint returns `valid:true` or is never reached. The `valid:false` branch in `TokenValidationService` is a safety net.

### 3.5 `/api/token/validate` vs `/api/auth/validate` vs how Node does it

| | `GET /api/token/validate` | `GET /api/auth/validate` | Node backend |
|---|---|---|---|
| Role comes from | the JWT claim | the database row | the JWT claim |
| Extra fields | `tokenType`, `issuedAt`, `expiresAt` | `isEmailVerified`, `isPhoneVerified` | — |
| Network call to jauth | — | — | **none**: `jwt.verify(token, JWT_SECRET, {algorithms:['HS512']})` locally |

Node never calls these endpoints on the request path. It verifies the signature itself because it holds the same secret. That's fast and avoids making jauth a single point of failure for every API call. The trade-off is that Node only learns about disabled or deleted users when it calls jauth (e.g. `validateWithJavaAuth` → `GET /api/users/me`) or when the token expires ([07](07-how-other-services-use-auth.md)).

---

## Try it yourself

1. `./run.sh local`
2. Postman folder **01** → *Register USER* (creates a user with a random phone)
3. Folder **04** → *Phone OTP · send*. Copy `OTP CODE` from the console into the `OTP` environment variable
4. Folder **04** → *Phone OTP · verify & login*
5. Folder **02** → *Validate token (JWT claims)*
6. Paste the `ACCESS_TOKEN` into [jwt.io](https://jwt.io) (local tokens only) to see the payload
7. Set a breakpoint in `OtpLoginService.sendPhoneLoginOtp` and `JwtAuthenticationFilter.doFilterInternal`, run in Debug from IntelliJ, and step through

Next: [09-configuration.md](09-configuration.md)
