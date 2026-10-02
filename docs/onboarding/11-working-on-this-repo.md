# 11 · Working on This Repo

How changes are made here, and where things go.

## 1. Ground rules

jauth is a **live production service**. Every FixHomi customer, provider and admin logs in through it, and the Node backend trusts every token it signs. Read [`AGENTS.md`](../../AGENTS.md) at the repo root: it defines the pre-flight protocol used for every change. In short:

1. **Re-validate against the live code**, not against a plan or an old doc.
2. **Map the blast radius:** who calls this? Check the other repos too: `noefi/fixhomi-backend`, `renfi/renfi`, `temp_admin`, `flapage` ([07](07-how-other-services-use-auth.md) lists every caller).
3. **Flag before proceeding** if a change could affect existing users or sessions, change the meaning of issued tokens, alter a schema/constraint on a table with data, or change an endpoint already in use.
4. **Prefer the smallest, additive, backward-compatible step.**
5. **Verify:** build, run tests, exercise the endpoint, and report the real result.

## 2. Branches and commits

- `main` is what's deployed. Never commit directly to it; branch off `main` (`feature/...`, `fix/...`, `docs/...`) and open a pull request on GitHub (`shreyastagram/jauth`).
- Recent history shows the commit style: `security(MED): …`, `feat(auth): …`, `fix(phone): …`. Use `git log --oneline` for examples.
- Never commit `.env`, `.localdb/`, or `target/`.

## 3. Daily loop

```bash
git checkout main && git pull
git checkout -b feature/<short-name>
./run.sh local                       # terminal 1
# edit → restart (Ctrl+C, ./run.sh local) or use IntelliJ Debug with hot-swap
./mvnw test                          # before every push
```

Test every endpoint you touch in Postman, and add or update its request in `postman/FixHomi_Auth_Service.postman_collection.json` so the collection keeps covering every endpoint.

## 4. Where code goes

| You need… | Put it in | Follow the pattern of |
|---|---|---|
| a new endpoint | a method on the controller that owns the URL prefix | `UserController`, `AdminUserController` |
| request/response bodies | a new class in `dto/` with Bean Validation annotations | `UpdateUserStatusRequest`, `UserProfileResponse` |
| business logic | a method on the matching service, `@Transactional` if it writes | `UserService.updateUserStatus` |
| a new query | a method on the repository (derived name or `@Query`) | `UserRepository.findByRoleIn` |
| a new table | an `@Entity` in `entity/` + a repository | `TrustedDevice` + `TrustedDeviceRepository` |
| an error with a specific HTTP status | throw an existing exception from `exception/` | see the table in [03](03-api-reference.md#errors-three-shapes) |
| a public (no-token) path | add it to the `permitAll()` list in `SecurityConfig` | lines 78–110 |
| a role restriction | URL rule in `SecurityConfig.authorizeHttpRequests` and/or `@PreAuthorize` on the method | `/api/admin/**` + `AdminUserController` |
| a new config value | `application.yaml` (`${ENV_VAR:default}`), read with `@Value` | `fixhomi.verification.otp.*` |

### Anatomy of an existing role-protected endpoint

Read this one end to end before you write your own (`PATCH /api/admin/users/{userId}/status`):

```java
// SecurityConfig.java:113 — URL gate
.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "IT_ADMIN")

// controller/AdminUserController.java:37–45 — method gate + validated body
@PatchMapping("/{userId}/status")
@PreAuthorize("hasAnyRole('ADMIN', 'IT_ADMIN')")
public ResponseEntity<UserProfileResponse> updateUserStatus(
        @PathVariable Long userId,
        @Valid @RequestBody UpdateUserStatusRequest request) {
    UserProfileResponse response = userService.updateUserStatus(userId, request);
    return ResponseEntity.ok(response);
}

// dto/UpdateUserStatusRequest.java
@NotNull(message = "isActive status is required")
private Boolean isActive;

// service/UserService.updateUserStatus — @Transactional; sets is_active and,
// when disabling, revokes all refresh tokens (refreshTokenService.revokeAllUserTokens)
```

`hasAnyRole('ADMIN', 'IT_ADMIN')` matches the authorities `ROLE_ADMIN` / `ROLE_IT_ADMIN` that `JwtAuthenticationFilter` built from the token's `role` claim ([04 §3](04-security-jwt-rbac.md#3-jwtauthenticationfilter-line-by-line)).

## 5. Getting the current user in code

```java
private Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return Long.valueOf(auth.getName());          // principal = user id as String
}
```

The role is available as `auth.getAuthorities()` (e.g. `ROLE_SUPPORT`, from the JWT). If you need the role as stored right now, load the `User` from `UserRepository` (`SessionController.validateToken` does this).

## 6. Testing

- `./mvnw test` runs `AuthServiceApplicationTests` under the `test` profile (`src/test/resources/application-test.yaml`). It starts the whole Spring context, so it catches wiring and configuration mistakes.
- That context-load test is currently the only automated test. The Postman collection is the end-to-end check: run it top to bottom (Collection Runner, with `RATE_LIMIT_ENABLED=false`), copying OTPs from the console when asked.
- `spring-boot-starter-test` (already in `pom.xml`) provides JUnit 5, Mockito, AssertJ, `@WebMvcTest` + `MockMvc` and `@DataJpaTest`. `spring-security-test` (which provides `@WithMockUser`) is not currently a dependency.

## 7. Debugging tips

| Want to see… | Do |
|---|---|
| why a request was denied | in `application-local.yaml` set `org.springframework.security: DEBUG`, restart, repeat the request |
| the SQL Hibernate runs | set `org.hibernate.SQL: DEBUG` (and `org.hibernate.orm.jdbc.bind: TRACE` for parameters) |
| what's in a token | decode the first two dot-separated parts (base64url) or use jwt.io for local tokens |
| what's in the DB | IntelliJ Database tool on `.localdb` ([06 §7](06-data-model.md#7-looking-at-the-data-locally)) |
| the OTP | console `OTP CODE:` line, or `SELECT otp FROM phone_otps ORDER BY id DESC` |
| the real HTTP status of a denied call | turn off redirect-following in Postman (Settings → *Automatically follow redirects*) |

## 8. Existing documents and their status

| Document | Status |
|---|---|
| `docs/onboarding/*` (this folder) | **Current**: verified against the code on 2026-10-02 |
| `AGENTS.md` | Current: working rules |
| `postman/` | Current: covers all 50 endpoints |
| `KNOWLEDGE_GRAPH.md` | Mostly correct reference written for AI agents; some details are older (says refresh tokens last 7 days; actual is 60) |
| `PHONE_SIGNUP_PLAN.md` | Internal plan and log of the phone-signup / unified-auth work (2026-07): useful history |
| `SECURITY_AUDIT.md` | Internal security audit and findings tracker. Treat as confidential |
| `AUDIT_LOG_FEATURE.md` | Plan for a feature that is not implemented in this repo |
| `docs/PHASE4_SESSION_TESTING_GUIDE.md` | Endpoints and bodies correct |
| `README.md` (old content), `ENVIRONMENT_VARIABLES.md`, `RENDER_DEPLOYMENT_GUIDE.md`, `DEPLOYMENT_CHECKLIST.md`, `DEPLOYMENT_READY.md`, `DOCKERFILE_VERIFICATION.md` | **Outdated**: wrong env var names, refresh lifetime and database story. Use [09-configuration.md](09-configuration.md) |
| `docs/NODEJS_INTEGRATION_GUIDE.md`, `docs/REACT_NATIVE_INTEGRATION_GUIDE.md`, `docs/REACT_NATIVE_STEP_BY_STEP_PLAN.md` | **Outdated**: list paths such as `/api/verification/*` and `/api/auth/token/*` that don't exist. Use [07](07-how-other-services-use-auth.md) |
| `docs/FULL_SYSTEM_ARCHITECTURE.md`, `docs/COMPLETE_PHASE_1_TO_4_TESTING_GUIDE.md` | Historical design/testing notes from January 2026 |

The outdated files carry a notice at the top pointing here.
