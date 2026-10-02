# jauth Onboarding: FixHomi Authentication Service

> Prepared for **Tejswita** (Intern, FixHomi) · Created by Claude with Shreyash · October 2026

Welcome to FixHomi. This folder is your guide to **jauth**: the Spring Boot service that signs every FixHomi user in and issues the tokens the rest of the platform trusts. It assumes you know Java and are new to this codebase.

Everything in these pages was checked against the code on `main` and against a running local instance: the endpoints, status codes, console output, database rows and Postman collection.

---

## Reading order

| # | Document | What you'll learn | Time |
|---|---|---|---|
| 01 | [Local setup](01-local-setup.md) | Run the service on your laptop, log in as admin, use Postman | 15 min hands-on |
| 02 | [Architecture & folder structure](02-architecture.md) | Where jauth fits, the package layout, the layering convention, profiles | 20 min |
| 03 | [API reference](03-api-reference.md) | All 50 endpoints: access, bodies, responses, errors | reference |
| 04 | [Security, JWT & RBAC](04-security-jwt-rbac.md) | Filter chain, token anatomy, how roles are enforced, the permission matrix, the SUPPORT role today | 30 min, **read carefully** |
| 05 | [Authentication flows](05-auth-flows.md) | What each flow does: register, logins, OTP, signup, Google/Apple, reset, delete, admin | 30 min |
| 06 | [Data model](06-data-model.md) | The 14 tables, relationships, how `role` is stored | 15 min |
| 07 | [How other services use jauth](07-how-other-services-use-auth.md) | React Native app, Node backend, admin panel, website: what they call and rely on | 20 min |
| 08 | [Walkthrough: send OTP → login → validate token](08-walkthrough-send-otp-and-validate-token.md) | One request traced through every layer, with real captured output | 30 min, then try it yourself |
| 09 | [Configuration](09-configuration.md) | Every env var and YAML setting, per profile | reference |
| 10 | [Behaviours & gotchas](10-behaviours-and-gotchas.md) | Surprising current behaviour that will save you debugging time | 15 min |
| 11 | [Working on this repo](11-working-on-this-repo.md) | Ground rules, branching, where code goes, testing, debugging | 15 min |

## Suggested first week

**Day 1: run it.**
- Do [01](01-local-setup.md) end to end.
- Run every Postman folder in order (00 → 13).
- Watch the console while you do: every OTP and email appears there.

**Day 2: understand it.**
- Read [02](02-architecture.md) and [08](08-walkthrough-send-otp-and-validate-token.md).
- Reproduce 08 with breakpoints in IntelliJ: `OtpLoginController.sendPhoneLoginOtp`, `OtpLoginService.sendPhoneLoginOtp`, `JwtAuthenticationFilter.doFilterInternal`, `TokenValidationService.validateToken`.

**Day 3: roles.**
- Read [04](04-security-jwt-rbac.md) and [06 §3](06-data-model.md#3-how-the-role-column-is-defined-in-each-database).
- In Postman, run folders 09 and 10: create a SUPPORT user as admin, log in as SUPPORT, and see what is allowed and what is denied.
- Decode the SUPPORT token and find each line of code that made each decision.

**Day 4: the wider system.**
- Read [07](07-how-other-services-use-auth.md).
- Open `noefi/fixhomi-backend/middlewares/adminAuthMiddleware.js` and `renfi/renfi/src/services/apiClient.js` and match them against what you learned.

**Day 5: the rest.**
- Read [05](05-auth-flows.md), [10](10-behaviours-and-gotchas.md) and [11](11-working-on-this-repo.md), plus `AGENTS.md` at the repo root.

## The service on one page

```
             ┌──────────────────────────── jauth (Spring Boot 3.4, Java 17) ────────────────────────────┐
 request ──► │ JwtAuthenticationFilter │ SecurityConfig rules │ RateLimiting │ Controller → Service → JPA │ ──► PostgreSQL / H2
             │ Bearer → userId + ROLE_x│ permitAll / admin    │  (per IP)    │ @Valid DTOs  @Transactional│
             └──────────────────────────────────────────────────────────────────────┬───────────────────┘
                                                                                     │ SmsService / EmailService
                                                                                     ▼ (MSG91 / Brevo, or console locally)
 Issues:  access token = HS512 JWT {userId, role, tokenType, sub=email, iat, exp(24h), iss}
          refresh token = opaque, 60 days, rotated on every use (45 s replay grace)
 Roles:   USER · SERVICE_PROVIDER · ADMIN · SUPPORT · IT_ADMIN   (/api/admin/** = ADMIN or IT_ADMIN)
 Trusted by: Node backend (verifies JWT locally with the shared JWT_SECRET), React Native app,
             admin panel (via Node), website
```

## Quick links

- Postman: `postman/FixHomi_Auth_Service.postman_collection.json` + `postman/FixHomi_Auth_Local.postman_environment.json`
- Local admin: `admin@fixhomi.local` / `LocalAdmin@123` (seeded by the `local` profile only)
- Health: `http://localhost:8080/api/auth/health`
- Repo rules: [`AGENTS.md`](../../AGENTS.md)

If anything in these docs doesn't match what you see when you run the code, the code wins. Tell Shreyash so the docs can be corrected.
