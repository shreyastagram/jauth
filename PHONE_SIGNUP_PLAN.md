# Phone-Number + OTP Signup — Implementation Plan & Tracker

**Status:** Phase 0 COMPILES on Mac (`./mvnw clean compile` → BUILD SUCCESS, 104 files, release 17).
Next: functional regression test (run app + verify endpoints). Branch `feature/phone-signup-users`.
**Last updated:** 2026-06-22

> ⚠️ **Before doing ANY work on this, read [`AGENTS.md`](./AGENTS.md).**
> The pre-flight protocol (re-validate → map blast radius → flag if risky → smallest safe step →
> verify → update tracker) is mandatory. This is a live app.

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done + verified · `[!]` flagged/blocked

---

## 0. ⭐ SESSION HANDOFF — RESUME HERE (read this first)

**Where we are (2026-06-22):**
- Building **phone-number + OTP signup for SERVICE USERS ONLY**. Providers are out of scope and must
  stay 100% untouched. Phone *login* already exists — we only build *signup*. Email is optional for
  users (bookings need phone-verified only); users can add+verify an email later via a **link** (not OTP).
- We're on branch **`feature/phone-signup-users`** (based off `feature/apple_devlopment`, **NOT `main`**;
  main is prod). Nothing has been merged or deployed.
- **Phase 0 (identity refactor: email → userId, email made nullable) is CODED and COMPILES**
  (`./mvnw clean compile` → BUILD SUCCESS, 104 files, release 17). 8 code files changed (see §9).
- Static re-verification done: all authed consumers parse principal as `Long.valueOf(auth.getName())`;
  filter always sets principal = userId string; OAuth2 success handler is a separate flow (unaffected).

**What's left to do, in order:**
1. **Functional-test Phase 0** from the terminal (see "Terminal test" below). This is the only
   remaining gate for Phase 0. Especially: login → `GET /api/users/me` must return the profile.
2. If green → mark Phase 0 `[x]` and start **Phase 1** (§5: the phone-signup endpoints, USER-only).
3. Phase 2 (RenFi UI, user screens only) → Phase 3 (add-email-later via link).

**Hard rules (also in `AGENTS.md` — follow them):**
- Re-validate against live code before editing; FLAG anything that could affect existing users/sessions
  or has unknown blast radius BEFORE changing it.
- **Do NOT touch providers.** Phone signup endpoint hard-rejects role ≠ USER.
- **Do NOT apply any DB migration locally** — H2 dev auto-applies the nullable change. The prod Postgres
  migration (`§4 step 0.10`) is applied **manually at deploy time only**, never by the agent.
- Phone-only users store email as **NULL, never `""`**.
- Keep the JWT **subject = email** (don't change it — NoeFix admin middleware depends on it).
- Update this tracker + the Validation Log after each step.

### Terminal test for Phase 0 (dev / H2 — fresh in-memory DB each run)
Run the app: `./mvnw spring-boot:run` (with your usual env). Default port is **8080** unless your
config overrides it — adjust `BASE` below. H2 starts empty, so register a user first, then exercise
the re-keyed (post-login) endpoints. **Expectation: all behave exactly as before the refactor.**
```bash
BASE=http://localhost:8080

# 1) Register a USER (returns accessToken + userId)
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' -d '{
  "email":"test1@example.com","password":"Test@1234","fullName":"Test User","role":"USER"
}'
# copy the accessToken from the response:
TOKEN=PASTE_ACCESS_TOKEN_HERE

# 2) *** THE KEY TEST *** — identity now resolves by userId, not email
curl -s $BASE/api/users/me -H "Authorization: Bearer $TOKEN"          # expect: your profile JSON

# 3) Update profile (name/phone)
curl -s -X PUT $BASE/api/users/profile -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"fullName":"Renamed User"}'  # expect: updated profile

# 4) Logged-in sessions
curl -s $BASE/api/auth/sessions -H "Authorization: Bearer $TOKEN"       # expect: session list

# 5) Email/password login again (separate from register)
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{
  "email":"test1@example.com","password":"Test@1234"
}'                                                                       # expect: tokens

# 6) Logged-in email verification trigger (dev email provider = stub → returns success)
curl -s -X POST $BASE/api/auth/email/send-verification -H "Authorization: Bearer $TOKEN"
```
PASS = every call returns its normal success response (no 401/500, `/api/users/me` shows the right user).
If any call fails, capture the request + response body + the server log line and report it.

---

## 1. Goal & Scope

Add a third signup method (alongside Google and manual email/password):
**phone number + OTP**, for **service USERS ONLY**.

> 🔒 **SCOPE LOCK (2026-06-22): Providers are OUT of scope and must remain 100% untouched.**
> Phone signup is for `role = USER` only. Provider signup and login flows — manual, Google,
> and the *existing* provider phone-OTP login — stay exactly as they are today. Any change that
> alters provider behaviour is a regression and must be `[!]` flagged. The phone-signup endpoint
> hard-rejects any role other than `USER`.
>
> 🔒 **LOGIN IS OUT OF SCOPE — only SIGNUP is built.** Service users **already** have working
> phone-OTP **login** (`OTPLoginScreen` → JAuth `sendPhoneLoginOtp`/`verifyPhoneLoginOtp`). We build
> **only phone signup** (account creation). After signup, users log in via the existing flow.
> ⚠️ Dependency: existing phone login generates a token with `setSubject(user.getEmail())` → **null**
> for phone-only users. **Phase 0 makes that existing login work for email-less users** (null-tolerant
> token + resolve-by-userId). Must be verified, or a phone-only user signs up but breaks on next login.

Client requirement: **true phone-only signup** — if a user signs up by phone, their **email is
left empty (stored as NULL, never `""`)**. They may add (and verify) an email later
("sign up by phone, collect email after").

> ⚠️ **Empty-email rule (applies everywhere):** a phone-only account's email is **NULL / absent**,
> never an empty string. Postgres partial unique index (`WHERE email IS NOT NULL`) and Mongo `sparse`
> unique both ignore NULL/absent but treat `""` as a real value → two `""` emails would collide.

## 2. Architecture recap

- **JAuth (Java/Spring/PostgreSQL)** — identity source of truth: `id` (BIGINT IDENTITY), `email`,
  `phone_number`, `role`, `is_email_verified`, `is_phone_verified`. Issues HS512 JWTs; secret shared with NoeFix.
- **NoeFix (Node/Express/MongoDB)** — business data keyed by `_id = JAuth userId.toString()`.
  Sync via `authServiceClient.js` → `profileSync.js` (`ensureProfileFromJavaAuth`); login self-heal + `withMongoRetry`.
- **RenFi (React Native)** — unified user+provider frontend. Phone-OTP **login** already works end-to-end.

**Central constraint:** identity is keyed on **email** (NOT NULL + unique, JWT subject, Spring
principal). Phone-only signup is impossible until identity keys on **userId** → that is Phase 0.

**Reused, do NOT rebuild:** OTP gen/verify/rate-limit/cleanup (`OtpLoginService`), MSG91 SMS,
phone normalization, Mongo sync + retry/self-heal, phone-OTP login.

## 3. Impact on existing users AND providers — NONE (must stay true)

- DB change only **relaxes** a constraint (email no longer required) — no row is rewritten/moved/deleted.
  Existing users and providers keep their emails; the nullable column never forces anyone null.
- Logged-in users / active sessions unaffected: Phase 0 keeps `userId` as a JWT claim (already present
  today) and resolves identity by `userId` with a **fallback to email** for legacy tokens. No forced logout.
- **Providers:** Phase 0 is shared auth infra and providers ride it safely (they have both `userId` claim
  AND email, so every resolve + fallback path works for them). Phases 1–3 add USER-only paths and do not
  touch any provider entity, route, screen, or the existing provider phone-OTP login.

> This section is a **guarantee to uphold**, re-checked at every step. If any step threatens it → `[!]` flag.

---

## 4. Phase 0 — Identity refactor (email → userId) + email nullable  `[~]` (coded, pending Mac verify)
**The backbone. Ships invisibly (no UI change). Existing flows must behave identically. Only phase with regression risk.**

### Pre-flight validation (do FIRST, before editing) `[~]`
- [x] Re-read current: `JwtAuthenticationFilter`, `JwtService`, `UserController`, `UserService`, `User` entity.
- [x] Grep ALL usages of `authentication.getName()`, `getCurrentUserEmail`, `findByEmail`, `getEmailFromToken`.
      **FINDING — blast radius bigger than documented.** Principal (`auth.getName()` = email) is also consumed by:
      `SessionController:213`, `VerificationController:64/82/99` (not just `UserController`). And the authenticated
      verification services `EmailVerificationService:58` / `PhoneVerificationService:64,115` are keyed by `userEmail`.
      So Phase 0 = ~8–9 files, not 4–5.
- [x] Confirm NoeFix `authMiddleware` prefers `userId` claim (line 159 `decoded.userId || decoded.sub`) — OK.
      **FINDING — do NOT change the JWT subject.** NoeFix `adminAuthMiddleware:32` reads `decoded.sub` AS email for
      the super-admin check, and `TokenValidationService:51` returns `claims.getSubject()`. Changing subject→userId
      would break those. **Decision:** keep subject = email; resolve identity inside JAuth's filter by the existing
      `userId` *claim* instead. Token contract unchanged → zero cross-repo impact, legacy tokens safe.
- [ ] Check how DB schema is managed (ddl-auto vs migrations) and whether a manual prod `ALTER` is required.
- [!] **FLAGGED to user (2026-06-22):** (a) expanded Phase 0 scope, (b) keep-subject-as-email decision,
      (c) phone-only users break the email-keyed phone-verification flow. Awaiting go-ahead before editing code.

### Implementation steps
> **Design decision (locked in pre-flight):** keep JWT **subject = email** (unchanged token contract).
> Resolve the authenticated user inside JAuth's filter by the existing **`userId` claim**, not the subject.
> This avoids breaking NoeFix `adminAuthMiddleware` / `TokenValidationService` and keeps legacy tokens valid.

- [x] **0.1** `security/JwtAuthenticationFilter.java` — resolve by `userId` claim (`findById`); fallback to
      `findByEmail(subject)` for legacy tokens missing the claim; set principal = **userId string**. *(blast radius: every authed request)*
- [x] **0.2** `security/JwtService.java` — left `setSubject(email)` AS-IS (JJWT drops a null subject safely); no change needed.
- [x] **0.3** `controller/UserController.java` — `getCurrentUserEmail()` → `getCurrentUserId()` (parse principal → Long); pass userId down.
- [x] **0.4** `service/UserService.java` — `getUserProfile`, `updateProfile`, `changePassword`,
      `requestDeleteAccountOtp`, `deleteAccountWithOtp`, `isUserAuthorizedForDeletion` → take `Long userId`; `findByEmail` → `findById`.
- [x] **0.5** `controller/SessionController.java` — principal → userId; resolve current user by id, not `findByEmail`.
- [x] **0.6** `controller/VerificationController.java` — principal → userId; pass userId to the verification services.
- [x] **0.7** `service/EmailVerificationService.java` + `service/PhoneVerificationService.java` — accept `userId`
      (or a resolved `User`) instead of `userEmail`, so the logged-in verify-email / verify-phone flows work for
      phone-only users. `sendVerificationEmail` must **fail gracefully when email is NULL** (clear error, no crash —
      ties to Phase 3 "add email first"). *(Email-input lookups in login / password-reset / Google / Apple stay keyed by email — unchanged.)*
- [x] **0.8** `service/TokenValidationService.java` — **confirmed NO change needed.** Returns `userId` + `getSubject()`
      (email) separately; with subject kept = email, NoeFix still gets `userId`; phone-only users return `email=null` (NoeFix uses userId).
- [x] **0.9** `entity/User.java` — `email` made nullable (dropped `@NotBlank` + `nullable=false`); unique index kept
      (Postgres/H2 allow multiple NULLs under a unique index, so no partial index needed as long as we store NULL not `""`).
- [ ] **0.10** DB migration (prod, applied manually — Hibernate `update` won't drop NOT NULL). **NOT YET APPLIED:**
      ```sql
      ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
      DROP INDEX IF EXISTS idx_email;
      CREATE UNIQUE INDEX idx_email ON users (email) WHERE email IS NOT NULL;
      ```

### Verification / regression checklist `[~]`
- [x] Project compiles — `./mvnw clean compile` on Mac → BUILD SUCCESS (104 files, release 17), 2026-06-22.
- [ ] Email/password login + all `/api/users/*` (profile, update, change-password, delete) work.
- [ ] Google + Apple signup/login unaffected.
- [ ] `/api/auth/otp/*` (logged-in phone verify) + `/api/auth/email/send-verification` + `/api/auth/sessions` still work.
- [ ] A pre-existing (legacy) access token still authenticates (fallback path).
- [ ] NoeFix auth still resolves the user (user path uses `userId` claim; admin/super-admin checks via `sub`=email intact).
- [ ] **Existing phone-OTP LOGIN works for an email-less user** (token built with null subject; session resolves by userId).
      *(Critical: this is the existing login flow, not new — but it must not break for phone-only users.)*
- [ ] **PROVIDER regression:** provider manual signup, Google signup, manual login, AND existing provider phone-OTP login all unchanged.

---

## 5. Phase 1 — Phone signup endpoints (JAuth + NoeFix)  `[ ]`

> 🔒 **USER-ONLY.** No provider entity/route/model/screen is touched. The JAuth signup endpoint
> hard-rejects any role ≠ `USER`. NoeFix creates only a Mongo **User** (never a Provider) via this path.

### Pre-flight validation `[ ]`
- [ ] Re-read `OtpLoginService`, `PhoneOtp`, `SecurityConfig`, NoeFix `authServiceClient`/`authController`/`authRoutes`.
- [ ] Confirm `PhoneOtp.user_id` is NOT NULL (it is) → justifies separate `PhoneSignupOtp`.
- [ ] Verify `buildUserInsertDoc` stores a missing email as **null/absent, not `""`** (empty-email rule §1).
- [ ] Decide account-creation point = on **verify** only (no orphan accounts).
- [x] **CRITICAL — confirmed no user-side email gate blocks booking.** Read `createRequest`
      (`traditionalServiceController.js:59`) + route (`traditionalServiceRoutes.js:44`, middleware = `authenticateToken`
      + `createRateLimiter` only). Booking guards = ownership + required fields + coordinates + Yavatmal geofence.
      **No email-verified check and no email requirement on the user-booking path.** `isFullyVerified` is a PROVIDER
      search filter, not a user gate. → Phone-only users can book. **Feature viable end-to-end. ✅**

### JAuth `[ ]`
- [ ] `entity/PhoneSignupOtp.java` (+ repo) — keyed by phone only: `phone_number, otp, expires_at, verified, attempts, full_name`.
- [ ] Signup service: `sendPhoneSignupOtp(phone, fullName)`, `verifyPhoneSignupOtp(phone, otp)` —
      create user on verify with **role = USER (hard-coded), email = NULL**, `isPhoneVerified=true`; reuse OTP/SMS/rate-limit.
- [ ] Controller: `POST /api/auth/signup/phone/send-otp`, `/verify`; add to `SecurityConfig` public allowlist.
- [ ] Duplicate handling: if phone already owned by ANY active+verified account (user OR provider) → reject ("please log in");
      reclaim an unverified number (mirror existing logic). *(No provider account is ever created here.)*

### NoeFix `[ ]`
- [ ] `utils/authServiceClient.js` — `sendPhoneSignupOtp()` / `verifyPhoneSignupOtp()` wrappers (with `callJavaAuth` retry).
- [ ] `controllers/authController.js` — `requestPhoneSignupOtp` + `verifyPhoneSignupAndSync` (USER only); on verify call JAuth
      then `ensureProfileFromJavaAuth` (role `user`); capture `termsAccepted`/`privacyAccepted`; store email as null/absent.
- [ ] `routes/authRoutes.js` — 2 **user** routes (send-otp + verify) under public rate limiter.
- [ ] `models/user.js` — `email` `required:true` → `sparse` unique. **`models/provider.js` UNCHANGED.**
- [ ] ~~`authMiddleware.js:67` provider email-guard~~ — **NOT NEEDED** (providers always have email; line stays correct).

### Verification `[ ]`
- [ ] Phone signup creates JAuth **User** + Mongo **User** profile (email null); duplicate path correct; non-USER role rejected.
- [ ] No Provider doc is created by this path; provider collection untouched.

---

## 6. Phase 2 — RenFi UI (USER screens only)  `[ ]`
> 🔒 Touch **user** registration screens only (`RegisterChoice` user flow / `RegisterScreen` /
> `UserAuthScreen`). Do **NOT** modify `ProviderRegisterScreen`, `ProviderAuthScreen`, or any provider route.
- [ ] User choice screen: add "Continue with phone number" (placement TBD by product).
- [ ] New phone-signup screen (name + phone, reuse `PhoneInput`); reuse `OTPVerifyScreen` with `context:'signup'`.
- [ ] `services/authService.js` — `sendPhoneSignupOtp()` / `verifyPhoneSignupOtp()`.
- [ ] `config/api.js` — add `OTP_SIGNUP` endpoints.
- [ ] i18n strings. (Login unchanged — phone-OTP login already works.)
- [ ] Style match (user accent): cards `borderRadius:14`, `#f67c16`, focus `#2563EB`, error `#EF4444`.
      Copy: "Continue with phone number", "Enter your mobile number", "We'll send you a verification code",
      "Enter the 6-digit code", "Verify", "Resend code".

## 7. Phase 3 — Add-email-later + verification (phone-only USERS)  `[ ]`
> **Now OPTIONAL / nice-to-have.** Bookings require **phone-verified only** (email no longer gates bookings),
> so phone-only users can book immediately. Email is for recovery/notifications. **Phase 3 can ship AFTER phase 1–2.**
> Confirmed today: `UpdateProfileRequest` has only `fullName` + `phoneNumber` — there is **no email field**, so
> "add email later" is genuinely new. Current email verification = a **link** emailed to the user (not OTP).
- [x] **DECISION (2026-06-22): use the existing email LINK** (`/api/auth/email/verify?token`), NOT OTP.
      Email is **updatable** but verification is **optional** (does not gate bookings).
- [ ] Extend `UpdateProfileRequest` / `UserService.updateProfile` to accept `email`: enforce uniqueness,
      set it (null → real email), trigger the existing **link-based** `EmailVerificationService` flow.
- [ ] NoeFix profile update + sync round-trips the verified flag.
- [ ] Only relevant to phone-only users (no email yet); providers and existing email users unaffected.

---

## 8. Risks & mitigations
- **Biggest:** a missed email-assuming path → regression. *Mitigation:* Phase 0 isolated + regression checklist before any phone UI.
- **Legacy tokens:** userId-then-email fallback in the auth filter.
- **Abuse/orphans:** account created only on OTP verify.
- **Empty-email uniqueness:** partial unique index (PG) + sparse unique (Mongo).

## 9. File-change summary (~16–19 files, mostly additive; providers untouched)
- **Phase 0 (JAuth, shared infra ~8–9 files):** `JwtAuthenticationFilter`, `JwtService` (null-email tolerant, subject
  unchanged), `UserController`, `UserService`, `SessionController`, `VerificationController`, `EmailVerificationService`,
  `PhoneVerificationService`, `User` entity + prod SQL migration. (`TokenValidationService` confirmed no-change.)
- **Phase 1 (JAuth):** `PhoneSignupOtp` (+repo), signup service, signup controller (USER-only), `SecurityConfig`.
  **(NoeFix):** `authServiceClient`, `authController`, `authRoutes`, `models/user.js` only. *(provider model + authMiddleware
  guard NOT needed — removed from scope.)*
- **Phase 2 (RenFi, USER screens only):** user choice screen, phone-signup screen, `OTPVerifyScreen` param, `authService`, `config/api`, i18n.
- **Phase 3:** `UpdateProfileRequest`, `UserService.updateProfile` (+ NoeFix passthrough).

---

## 10. Validation Log (append-only — what was checked, found, flagged)
- 2026-06-22 — Plan + AGENTS.md created. No code changed yet.
- 2026-06-22 — Phase 0 pre-flight grep sweep done. Findings:
  1. **Scope bigger than documented:** email-principal also consumed by `SessionController` +
     `VerificationController`; verification services keyed by `userEmail`. Phase 0 now ~8–9 files.
  2. **Token-contract decision:** keep JWT subject = email; resolve by `userId` claim in the filter only.
     Changing the subject would break NoeFix `adminAuthMiddleware` (reads `sub` as email) + `TokenValidationService`.
  3. **Phone-only gap:** `PhoneVerificationService` is email-keyed → must be re-keyed to userId for email-less users.
  No code changed. **FLAGGED to user; awaiting go-ahead before editing.**
- 2026-06-22 — **SCOPE LOCK: phone signup/login = USERS ONLY; providers fully untouched (user chose Option A).**
  Re-analysed + re-validated against code. Changes from this:
  1. `TokenValidationService` re-read → **confirmed no change needed** (returns userId + email separately; subject stays = email).
  2. `VerificationController:64/82/99` re-read → confirms re-key to userId; `email/send-verification` must fail gracefully on null email.
  3. **New robustness rule:** phone-only email must be **NULL, never `""`** (partial/sparse unique would collide on `""`).
  4. Phase 1 trimmed: dropped `models/provider.js` change + `authMiddleware.js:67` guard (not needed — providers always have email).
     JAuth signup hard-rejects role ≠ USER. Phase 2 = user screens only. Net ~16–19 files (was ~18–22).
  Plan updated accordingly. Still no production code changed. Phase 0 ready to start on go-ahead.
- 2026-06-22 — Clarified: service users **already have phone-OTP LOGIN** → login fully out of scope; build **signup only**.
  Key dependency recorded: existing login's `generateAccessToken(userId, email, role)` sets subject=null for phone-only
  users → Phase 0 (null-tolerant token + resolve-by-userId) is what keeps that existing login working for them.
  Added explicit Phase 0 verification item. No production code changed.
- 2026-06-22 — Verified email/profile facts for Phase 3: `UpdateProfileRequest` has only fullName+phoneNumber (no email
  field today) → "add email later" is new. Email verification today = **link** (`/api/auth/email/verify?token`), not OTP.
  Booking gate check: verification gates found are PROVIDER-side; no user-side email-verified block seen in main booking
  controller (consistent with user: bookings = phone-verified only). → Phase 3 demoted to OPTIONAL/after-Phase-1-2;
  added Phase 1 pre-flight item to confirm exact user-booking path. OTP-vs-link decision left PENDING. No code changed.
- 2026-06-22 — Decisions locked by user: email **updatable**, verification via **link** (not OTP); Phase 3 IN scope.
  Booking-gate cross-check PASSED (read `createRequest` — no user-side email gate). Approved Q1 (re-key) + Q2 (migration).
- 2026-06-22 — **Phase 0 CODED (8 files) + committed/pushed** to branch `feature/phone-signup-users`
  (off `feature/apple_devlopment`; NOT `main`). Not compiled on this box (Java/Maven toolchain mismatch — only JDK8 on
  PATH, only-script mvnw needs download). **Compile + functional test happens on Mac.** Migration 0.10 NOT yet applied.
  ⚠️ FLAG to user: base branch is `feature/apple_devlopment`, not `main` — confirm that's the intended base.
- 2026-06-22 — **Phase 0 COMPILES on Mac** (`./mvnw clean compile` → BUILD SUCCESS, 104 files, release 17).
- 2026-06-22 — Static runtime re-verification (grep of principal usage): all authed consumers parse
  `Long.valueOf(auth.getName())`; filter always sets principal = userId string; `OAuth2AuthenticationSuccessHandler`
  uses a separate OAuth2 login principal (unaffected). Residual runtime check = JJWT returning `userId` claim as Long,
  covered by the first functional test (login → `/api/users/me`). **Functional test still PENDING — Phase 0 not yet `[x]`.**
- 2026-06-22 — **Work continuing in a NEW session on Mac.** Handoff written (§0). Next action there:
  run the Terminal test for Phase 0, then proceed to Phase 1 if green.
