# 02 · Architecture and Folder Structure

## 1. What this service is

**jauth** (repository folder `jarbac`, Maven artifact `com.fixhomi:auth-service`) is FixHomi's **identity service**. It owns:

- the `users` table: email, phone, password hash, **role**, active/verified flags;
- issuing and rotating **tokens**: a 24-hour JWT access token and a 60-day refresh token;
- every way of proving identity: email + password, phone + password, phone OTP, email OTP, phone signup, Google, Apple, plus password reset, phone/email verification and account deletion;
- **role-based access control (RBAC)** for its own admin endpoints.

It does **not** own profiles, addresses, bookings, payments or service requests. Those live in the Node.js backend and MongoDB.

## 2. Where it sits in FixHomi

```
                    ┌──────────────────────────────┐
                    │  React Native app (renfi)    │
                    │  authClient ──────────┐      │
                    │  apiClient ───┐       │      │
                    └───────────────┼───────┼──────┘
                                    │       │  login / OTP / refresh / me / sessions
                     business APIs  │       ▼
┌──────────────────────┐            │   ┌───────────────────────────────┐
│ Admin panel          │──────┐     │   │  jauth  (this repo)           │
│ (temp_admin, Vite)   │      │     │   │  Spring Boot 3.4 · Java 17    │
└──────────────────────┘      ▼     ▼   │  issues HS512 JWTs            │
                    ┌───────────────────┐│  PostgreSQL (Neon) in prod   │
                    │ Node.js backend   ││  H2 locally                  │
                    │ (noefi)           │└───────────────┬──────────────┘
                    │ verifies JWTs     │  server-to-    │
                    │ LOCALLY with the  │  server calls  │   SMS: MSG91
                    │ shared JWT_SECRET │───────────────►│   Email: Brevo
                    │ MongoDB           │                │
                    └───────────────────┘                │
┌──────────────────────┐                                 │
│ Website (flapage,    │── account deletion / OTP ──────►┘
│ Next.js, server-side)│
└──────────────────────┘
```

The key idea: **jauth signs tokens; everyone else trusts the signature.** The Node backend does not call jauth on every request. It verifies the JWT itself with the same `JWT_SECRET` and reads the `userId` and `role` claims. Details are in [07-how-other-services-use-auth.md](07-how-other-services-use-auth.md).

| Environment | jauth URL | Paired Node URL |
|---|---|---|
| Production | `https://auth.fixhomi.com` | `https://api.fixhomi.com` |
| Dev/staging | `https://jauth-1.onrender.com` | `https://noefix-1-dev.onrender.com` |
| Local | `http://localhost:8080` | `http://localhost:5001` |

(Source: `renfi/renfi/src/config/environment.js`.)

## 3. Tech stack (from `pom.xml`)

| Concern | Library / version |
|---|---|
| Framework | Spring Boot **3.4.12** (Web, Security, Data JPA, Validation, OAuth2 Client, Actuator) |
| Language | Java **17** |
| JWT | `io.jsonwebtoken:jjwt` **0.11.5** (api + impl + jackson) |
| Databases | PostgreSQL driver (prod, Neon) · H2 (local/tests) |
| Schema management | Hibernate `ddl-auto: update`. There is **no Flyway/Liquibase**. |
| Rate limiting | Bucket4j 8.7.0 (in-memory, per IP) |
| Google token verification | `com.google.api-client` 2.2.0 |
| API docs | springdoc-openapi 2.8.8 (`/swagger-ui.html`; requires a token locally, disabled in prod) |
| Build | Maven Wrapper (Maven 3.9.11) |
| Runtime | Docker (`eclipse-temurin:17-jre-alpine`) on Render |

There is no Lombok. Entities and DTOs have hand-written getters and setters.

## 4. Repository layout

```
jarbac/
├── pom.xml                          Maven build (dependencies above)
├── mvnw, mvnw.cmd, .mvn/            Maven Wrapper
├── run.sh                           Local launcher: loads .env, checks JWT_SECRET, picks profile
├── .env.local.example               Template for YOUR local .env  (cp → .env)
├── .env.example                     Template for the PRODUCTION env (Render) — not for local use
├── Dockerfile, .dockerignore        Image used by Render (tests skipped in the image build)
├── render.yaml                      Render blueprint (prod profile, Brevo, MSG91)
├── postman/                         Postman collection + local environment (every endpoint)
├── docs/
│   ├── onboarding/                  ← you are here
│   └── *.md                         Older integration guides (see onboarding README for status)
├── AGENTS.md                        Working rules for changes to this live service — read it
├── KNOWLEDGE_GRAPH.md, PHONE_SIGNUP_PLAN.md, SECURITY_AUDIT.md   Internal history / audits
└── src/
    ├── main/
    │   ├── java/com/fixhomi/auth/   (package tree below)
    │   └── resources/
    │       ├── application.yaml         Base config (H2 in-memory, all defaults)
    │       ├── application-local.yaml   Local dev profile (file H2, stubs, seeded admin)
    │       └── application-prod.yaml    Production profile (Neon PostgreSQL, real providers)
    └── test/
        ├── java/com/fixhomi/auth/AuthServiceApplicationTests.java   context-load test
        └── resources/application-test.yaml                         test profile
```

### Package tree: `com.fixhomi.auth`

```
AuthServiceApplication        @SpringBootApplication @EnableScheduling (scheduled cleanup jobs)

config/                       Spring wiring
  SecurityConfig              Filter chain, permit-list, /api/admin/** rule, CORS, BCrypt(12)
  RateLimitingFilter          Bucket4j per-IP limits (auth / otp / general)
  EmailServiceConfig          Chooses BrevoEmailService or StubEmailService
  SmsServiceConfig            Chooses Msg91SmsService or StubSmsService
  OpenApiConfig               Swagger metadata + bearer scheme
  JpaConfig                   @EnableJpaAuditing (createdAt / updatedAt)
  LocalDevDataSeeder          @Profile("local") — seeds the local ADMIN account
  JwtProperties, FixhomiProperties   @ConfigurationProperties holders (values are read via @Value elsewhere)

controller/                   HTTP layer — 9 controllers, 50 endpoints
  AuthController              /api/auth: register, login, login/phone, refresh, logout, health
  OtpLoginController          /api/auth/login: phone & email OTP login
  PhoneSignupController       /api/auth/signup: phone-number signup (USER only)
  OAuth2Controller            /api/auth/oauth2: Google & Apple mobile sign-in, Apple email OTP
  VerificationController      /api/auth: phone/email verification, forgot/reset password (3 ways)
  SessionController           /api/auth: sessions, trusted devices, /validate
  TokenController             /api/token: validate, me
  UserController              /api/users: me, profile, email, phone change, password, delete
  AdminUserController         /api/admin/users: create staff, list, get, enable/disable, hard delete

dto/                          Request/response records with Bean Validation annotations
entity/                       JPA entities (14) + Role enum
repository/                   Spring Data JPA interfaces (13)
security/
  JwtService                  Create/parse/validate HS512 JWTs
  JwtAuthenticationFilter     Bearer token → SecurityContext (runs on every request)
  TokenHasher                 SHA-256 for reset / email-verification link tokens
  OAuth2Authentication{Success,Failure}Handler   Browser Google OAuth2 flow
service/                      Business logic (one service per flow)
  AuthService                 register, password login, lockout
  OtpLoginService             phone/email OTP login
  PhoneSignupService          phone signup
  PhoneVerificationService    verify stored phone, change phone
  EmailVerificationService    email verification links
  PasswordResetService        reset by link, phone OTP, email OTP
  GoogleAuthService, AppleAuthService, AppleEmailVerificationService
  RefreshTokenService         create / rotate (45 s grace) / revoke refresh tokens
  SessionService              sessions & trusted devices
  TokenValidationService      token introspection
  UserService                 profile, email, password change, delete, admin operations
  notification/
    EmailService, SmsService  Interfaces
    BrevoEmailService, Msg91SmsService    Real providers (prod)
    StubEmailService, StubSmsService      Print to console (local)
exception/
  GlobalExceptionHandler      @RestControllerAdvice: exception → HTTP status + ErrorResponse
  AuthenticationException(401) InvalidRoleException(403) ResourceNotFoundException(404)
  DuplicateResourceException(409) InvalidPasswordException(400) VerificationException(400)
  TooManyRequestsException(429) ErrorResponse
```

## 5. The layering convention

Every feature follows the same path. Match it when you add code:

```
HTTP request
  → Spring Security chain (JwtAuthenticationFilter, rules) → RateLimitingFilter [security/, config/]
  → @RestController method, @Valid @RequestBody SomeRequest DTO               [controller/, dto/]
  → @Service method, usually @Transactional                                   [service/]
      → Spring Data repository → JPA entity → table                           [repository/, entity/]
      → JwtService / RefreshTokenService (to issue tokens)                    [security/, service/]
      → SmsService / EmailService (to notify)                                 [service/notification/]
  ← DTO returned as JSON
  ← or an exception, turned into JSON by GlobalExceptionHandler               [exception/]
```

Conventions you'll see in the code:

- **Who is calling.** Controllers never trust a user id from the body. They read it from the security context, where the principal is the user id as a string:
  ```java
  Long userId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
  ```
- **Validation** is declarative on DTOs (`@NotBlank`, `@Pattern`, `@Size`). A failure becomes HTTP 400 with `validationErrors` per field.
- **Errors** are thrown as the custom exceptions in `exception/` and mapped centrally. Some newer controllers (OTP login, phone signup, forgot-password by phone/email) catch exceptions themselves and return `{success:false, code, message}` maps. The mobile app depends on those `code` values.
- **Dependency injection** uses field `@Autowired` in older classes and constructor injection in newer ones. Both are present.
- **Scheduled jobs** (`@Scheduled`) inside services delete expired OTPs, lockouts and refresh tokens every 10–30 minutes.
- **Phone numbers** are normalised to 10 digits by `User.normalizePhoneNumber` (strips `+91`/`91`) in a `@PrePersist`/`@PreUpdate` hook. Emails are lowercased in the same hook.

## 6. Profiles

| Profile | Activated by | Database | Notifications | Used for |
|---|---|---|---|---|
| *(none / default)* | plain `./mvnw spring-boot:run` | H2 in-memory | stub | — (needs `GOOGLE_CLIENT_ID` to start) |
| `local` | `./run.sh local` | H2 file `./.localdb` | stub (console) | **your daily development** |
| `test` | `@ActiveProfiles("test")` | H2 in-memory | stub | `./mvnw test` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` on Render | Neon PostgreSQL (`sslmode=require`) | Brevo + MSG91 | deployed services on Render (`render.yaml`) |

All configuration values and their environment variables are in [09-configuration.md](09-configuration.md).

## 7. Deployment in one paragraph

Render builds the `Dockerfile`: a Maven stage runs `mvn clean package -DskipTests`, then a JRE 17 Alpine stage runs `java -XX:MaxRAMPercentage=75 -jar app.jar` as a non-root user, with a `curl /actuator/health` health check. `SPRING_PROFILES_ACTIVE=prod` selects `application-prod.yaml`. Secrets (`JWT_SECRET`, database credentials, Brevo/MSG91 keys, Google/Apple client ids) are set in the Render dashboard. Hibernate `ddl-auto: update` adds new tables and columns on startup. It never drops or alters existing ones.

Next: [03-api-reference.md](03-api-reference.md)
