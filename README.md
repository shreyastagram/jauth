# FixHomi Auth Service (jauth)

The identity service for FixHomi. It registers and signs in every customer, service provider and staff member, issues the JWTs the rest of the platform trusts, and enforces role-based access to its admin API.

| | |
|---|---|
| Stack | Spring Boot 3.4.12 · Java 17 · Spring Security 6 · jjwt 0.11.5 (HS512) · Hibernate 6.6 |
| Database | PostgreSQL (Neon) in production · H2 locally |
| Notifications | MSG91 (SMS) · Brevo (email) · console stubs locally |
| Deployed | Render (Docker) at `https://auth.fixhomi.com` |
| Consumers | React Native app · Node.js backend · admin panel (via Node) · website |

## Quick start (local)

Requirements: JDK 17. Maven comes with the wrapper.

```bash
cp .env.local.example .env
# set JWT_SECRET in .env to the output of:
openssl rand -base64 64 | tr -d '\n'

./run.sh local
curl http://localhost:8080/api/auth/health
# {"status":"UP","message":"Auth service is running"}
```

The `local` profile gives you:
- a file-based H2 database in `./.localdb`;
- OTPs and emails **printed to the console** instead of sent;
- a seeded admin account: `admin@fixhomi.local` / `LocalAdmin@123`.

Then import `postman/FixHomi_Auth_Service.postman_collection.json` and `postman/FixHomi_Auth_Local.postman_environment.json` into Postman. The collection covers every endpoint and runs top to bottom.

Run the tests with `./mvnw test`.

## Documentation

Start with **[docs/onboarding/](docs/onboarding/README.md)**. New to the project? Read the 7-slide overview first: [docs/onboarding/kt-deck/jauth-kt-deck.pdf](docs/onboarding/kt-deck/jauth-kt-deck.pdf).

| | |
|---|---|
| [01 Local setup](docs/onboarding/01-local-setup.md) | [07 How other services use jauth](docs/onboarding/07-how-other-services-use-auth.md) |
| [02 Architecture & folder structure](docs/onboarding/02-architecture.md) | [08 Walkthrough: send OTP → validate token](docs/onboarding/08-walkthrough-send-otp-and-validate-token.md) |
| [03 API reference (50 endpoints)](docs/onboarding/03-api-reference.md) | [09 Configuration](docs/onboarding/09-configuration.md) |
| [04 Security, JWT & RBAC](docs/onboarding/04-security-jwt-rbac.md) | [10 Behaviours & gotchas](docs/onboarding/10-behaviours-and-gotchas.md) |
| [05 Authentication flows](docs/onboarding/05-auth-flows.md) | [11 Working on this repo](docs/onboarding/11-working-on-this-repo.md) |
| [06 Data model](docs/onboarding/06-data-model.md) | |

Before changing anything, read [AGENTS.md](AGENTS.md). This is a live production service.

## API at a glance

| Prefix | Controller | Purpose |
|---|---|---|
| `/api/auth` | `AuthController` | register, login (email/phone + password), refresh, logout, health |
| `/api/auth/login` | `OtpLoginController` | passwordless phone/email OTP login |
| `/api/auth/signup` | `PhoneSignupController` | phone-number signup (USER) |
| `/api/auth/oauth2` | `OAuth2Controller` | Google / Apple mobile sign-in |
| `/api/auth` | `VerificationController` | phone/email verification, forgot/reset password |
| `/api/auth` | `SessionController` | sessions, trusted devices, `/validate` |
| `/api/token` | `TokenController` | token introspection |
| `/api/users` | `UserController` | the caller's own account |
| `/api/admin/users` | `AdminUserController` | staff creation and user administration (ADMIN / IT_ADMIN) |

Roles: `USER`, `SERVICE_PROVIDER`, `ADMIN`, `SUPPORT`, `IT_ADMIN`.

## Project layout

```
src/main/java/com/fixhomi/auth/
  config/       security config, rate limiting, provider wiring, local seeder
  controller/   9 REST controllers
  dto/          request/response classes with validation
  entity/       JPA entities + Role enum
  repository/   Spring Data repositories
  security/     JWT service + filter, OAuth2 handlers, token hashing
  service/      business logic; notification/ = SMS & email providers
  exception/    custom exceptions + GlobalExceptionHandler
src/main/resources/
  application.yaml, application-local.yaml, application-prod.yaml
postman/        collection + local environment
docs/onboarding/  current documentation
```

## Deployment

Render builds the `Dockerfile` (tests skipped in the image build) and runs it with `SPRING_PROFILES_ACTIVE=prod`. Secrets are set in the Render dashboard. `JWT_SECRET` must be identical to the Node backend's. See [09 Configuration](docs/onboarding/09-configuration.md).
