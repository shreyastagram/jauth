# Phone-Number + OTP Signup — Implementation Plan & Tracker

**Status:** Phase 0 + Phase 1 + Phase 2 all coded & committed.
- JAuth `feature/phone-signup-users`: code commit `c752c96` + tracker commit `0cce23d`.
- NoeFix `phone-signup-providers` (name to be fixed): code commit `bdc320c`.
- RenFi `feature/phone-signup-users-ui` (cut off `backgroun-tracking-update-fe`): commit `fbafaed`.
Backend e2e (22 assertions) GREEN. UI committed; device/Metro smoke deferred to user.
Pending prod deploy steps = §4.0.10 Postgres ALTER + Mongo `email` sparse-unique index rebuild.
Nothing pushed.
**Last updated:** 2026-06-24

> ▶️ **RESUME HERE NEXT SESSION:** the *code* is done and the **backend is fully validated on an isolated dev
> env**. As of 2026-06-24 a **dev-pointed build is going out to testers** via Firebase App Distribution
> (`USE_DEV_STAGING=true` at build time; testers create accounts in the dev DB). **We are now waiting on tester
> feedback.**
>
> 🟢 **WHEN TESTING PASSES AND YOU WANT TO GO LIVE — do these, in order (details in §12):**
> 1. **Push/merge** the 3 feature branches to whatever each prod service deploys from (§12 Step 0). Confirm prod
>    deploys from `main`, not the feature branches.
> 2. **Prod Postgres migration — ONE line:** `ALTER TABLE users ALTER COLUMN email DROP NOT NULL;` (run on PROD
>    Neon; verified safe + sufficient on the dev prod-copy branch — no index rebuild needed). §12 Step 2.
> 3. **Deploy** JAuth + NoeFix to prod; smoke an existing email login + a throwaway phone signup. §12 Step 3.
> 4. **RenFi release build:** set `renfi/src/config/environment.js` `USE_DEV_STAGING = false` (git already has
>    false — just don't ship a `true` build), **fix the iOS build-number regression** (`CURRENT_PROJECT_VERSION`
>    was dropped 14→1; must be > the last App Store build), build, final smoke against PROD.
> 5. **Submit** to Play Store + App Store. §12 Steps 4–5.
>
> See **§12 GO-LIVE / DEPLOY CHECKLIST** for the full runbook, and **`renfi/renfi/PHONE_SIGNUP_TEST_PLAN.md`** for
> the device test matrix.

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

## 4. Phase 0 — Identity refactor (email → userId) + email nullable  `[x]` (coded + compiled + functionally verified)
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
- [x] Email/password login + `/api/users/me` + `/api/users/profile` PUT all return 200 with correct data (2026-06-22, dev/H2).
- [ ] Google + Apple signup/login unaffected. *(Out of scope to test locally without OAuth set-up; static review covered in §10 entry.)*
- [x] `/api/auth/email/send-verification` + `/api/auth/sessions` return 200 (2026-06-22). `/api/auth/otp/*` (logged-in phone verify) not yet exercised end-to-end; static re-key covered by §0.7.
- [ ] A pre-existing (legacy) access token still authenticates (fallback path). *(Cannot synthesize legacy token without secret leak; covered by code path + static review.)*
- [ ] NoeFix auth still resolves the user (user path uses `userId` claim; admin/super-admin checks via `sub`=email intact). *(Cross-repo check — will validate during Phase 1 NoeFix wiring.)*
- [ ] **Existing phone-OTP LOGIN works for an email-less user** (token built with null subject; session resolves by userId).
      *(Cannot be exercised until Phase 1 creates the first phone-only user. Will verify as part of Phase 1 acceptance.)*
- [ ] **PROVIDER regression:** provider manual signup, Google signup, manual login, AND existing provider phone-OTP login all unchanged. *(Same JAuth filter is shared infra — token contract unchanged; full provider regression to be re-validated end of Phase 1.)*

---

## 5. Phase 1 — Phone signup endpoints (JAuth + NoeFix)  `[ ]`

> 🔒 **USER-ONLY.** No provider entity/route/model/screen is touched. The JAuth signup endpoint
> hard-rejects any role ≠ `USER`. NoeFix creates only a Mongo **User** (never a Provider) via this path.

### Pre-flight validation `[~]`
- [x] Re-read `OtpLoginService`, `PhoneOtp`, `PhoneOtpRepository`, `SecurityConfig`, `User`, `UserRepository`,
      NoeFix `authServiceClient`/`authController`/`authRoutes`/`utils/profileSync.js`/`models/user.js` (2026-06-22).
- [x] `PhoneOtp.user_id` IS NOT NULL → separate `PhoneSignupOtp` justified (2026-06-22).
- [!] `buildUserInsertDoc` is fine (no email key) BUT `normalizeJavaUser` + the `$or` lookup + the `throw` in
      `ensureProfileFromJavaAuth` all assume email is present and coerce null→`""`. **VIOLATES §1 NULL rule —
      must be fixed in Phase 1.** Adds `utils/profileSync.js` to scope. **FLAGGED, awaiting go-ahead.**
- [ ] Decide account-creation point = on **verify** only (no orphan accounts).
- [x] **CRITICAL — confirmed no user-side email gate blocks booking.** Read `createRequest`
      (`traditionalServiceController.js:59`) + route (`traditionalServiceRoutes.js:44`, middleware = `authenticateToken`
      + `createRateLimiter` only). Booking guards = ownership + required fields + coordinates + Yavatmal geofence.
      **No email-verified check and no email requirement on the user-booking path.** `isFullyVerified` is a PROVIDER
      search filter, not a user gate. → Phone-only users can book. **Feature viable end-to-end. ✅**

### JAuth `[x]` (compiled + functionally verified 2026-06-22)
- [x] `entity/PhoneSignupOtp.java` + `repository/PhoneSignupOtpRepository.java` — keyed by phone only;
      table `phone_signup_otps` (auto-created by Hibernate, verified in H2 startup log).
- [x] `service/PhoneSignupService.java`: `sendPhoneSignupOtp(phone, fullName)`, `verifyPhoneSignupOtp(phone, otp)` —
      user created on verify with **role = USER hard-coded, email = NULL, hasPassword = false**, `isPhoneVerified=true`;
      reuses `SmsService`, OTP-rate-limit + cleanup pattern from `OtpLoginService`.
- [x] `controller/PhoneSignupController.java`: `POST /api/auth/signup/phone/send-otp` + `/verify`; added to `SecurityConfig`
      public allowlist (mirrors `/api/auth/login/phone/*`).
- [x] Duplicate handling verified: re-send for the same phone returns **409 `ALREADY_REGISTERED`**;
      unverified-phone reclaim runs at both send AND verify time (race-safe).
- [x] **Phase 0 critical check now PASSED:** existing `POST /api/auth/login/phone/send-otp` + `/verify` works for
      the phone-only user (null email → JWT with no `sub` → identity resolves via `userId` claim → `/api/users/me` returns 200).
- [x] Regression: classic email/password register still 201s; both phone-only (userId=1, email=null) and
      email user (userId=2, email present) coexist in the same H2 DB.

### NoeFix `[x]` (coded + full Mongo e2e PASSED via mongodb-memory-server)
- [x] `utils/profileSync.js` — `normalizeJavaUser` no longer coerces null email to `""`; `ensureProfileFromJavaAuth`
      no longer throws for null email (USER-only path); email branch dropped from `$or` lookup + `identitySet` when null.
      Providers still required to have email (defensive throw retained for `role === 'provider'`).
- [x] `utils/authServiceClient.js` — `sendPhoneSignupOtp()` / `verifyPhoneSignupOtp()` wrappers (using `callJavaAuth` retry).
- [x] `controllers/authController.js` — `requestPhoneSignupOtp` + `verifyPhoneSignupAndSync` (USER only); on verify
      calls `ensureProfileFromJavaAuth(role='user')`; captures `termsAccepted`/`privacyAccepted` (rejects 400
      `LEGAL_ACCEPTANCE_REQUIRED` if absent); patches legal metadata onto the Mongo doc; defensive `role !== 'USER'` guard.
- [x] `routes/authRoutes.js` — 2 **user** routes (`/api/auth/signup/phone/send-otp` + `/verify`) under `publicRateLimiter`.
- [x] `models/user.js` — `email: required:true` → `sparse: true` (unique kept). **`models/provider.js` UNCHANGED.**
- [ ] ~~`authMiddleware.js:67` provider email-guard~~ — **NOT NEEDED** (providers always have email; line stays correct).

### Verification `[x]`
- [x] **JAuth side verified live (dev/H2):** signup send-otp → 200; verify → JAuth User created with
      `email=null, role=USER, isPhoneVerified=true, hasPassword=false`. Duplicate path → 409 `ALREADY_REGISTERED`.
      Coexists with classic email user in same DB. Existing phone LOGIN works for the new phone-only user.
- [x] **NoeFix wrapper layer verified live (against local JAuth):** `sendPhoneSignupOtp` + `verifyPhoneSignupOtp`
      wrappers + their controller error mapping (409 `PHONE_ALREADY_EXISTS`, OTP_EXPIRED, etc.) — `/tmp/phase1_wrapper_test.js`.
- [x] All 5 edited NoeFix files pass `node --check` (syntax-clean).
- [x] **FULL NoeFix → JAuth → Mongo end-to-end (`/tmp/phase1_full_e2e.js`)** via `mongodb-memory-server` (installed
      as a NoeFix devDependency). **22 assertions across 5 scenarios all PASSED:**
      (A) phone-only signup writes Mongo doc with `email: undefined` (NOT `""`), `phone` normalized to 10 digits,
          `_id === javaUserId`, override fields persisted;
      (B) regression — classic email register still produces a Mongo doc with the lowercased+trimmed email;
      (C) `ensureProfileFromJavaAuth` is idempotent (re-sync of the same JAuth user does NOT insert a duplicate);
      (D) Phase 3 forward-compat — a phone-only user later gaining an email is patched onto the existing doc;
      (E) two phone-only users with no email both insert successfully under the manually-created sparse unique
          email index (proves the §4 prod index-rebuild step achieves its goal).
- [ ] **Provider regression (in dev):** confirm `/api/auth/provider/*` flows unchanged. *(No provider code was touched
      in either repo, but a smoke run in dev confirms behaviour.)*

---

## 6. Phase 2 — RenFi UI (USER screens only)  `[x]` (coded + committed; awaits Metro/device smoke)
> 🔒 Touch **user** registration screens only. Providers remain 100% untouched: `RegisterChoice` is shared,
> but the new "Continue with phone" card is gated on an optional `onPickPhone` prop that only
> `RegisterScreen` passes — `ProviderRegisterScreen` does not, so the provider flow renders identically.
- [x] User choice screen — added "Continue with phone number" card with blue `#2563EB` accent + matching icon circle.
- [x] New `src/screens/PhoneSignupScreen.jsx` (name + phone, reuses `PhoneInput`).
- [x] `OTPVerifyScreen` reused with new optional `context:'signup'` + `fullName` + `signupExtras` props; existing
      login flow is the default branch and stays untouched.
- [x] `services/authService.js` — `sendPhoneSignupOtp(phone, fullName)` + `verifyPhoneSignupOtp(phone, otp, extras)`.
      Both go via `apiClient` (NoeFix) so the Mongo profile upsert runs.
- [x] `config/api.js` — `OTP_SIGNUP.PHONE_SEND_OTP` + `OTP_SIGNUP.PHONE_VERIFY` (NoeFix paths).
- [x] `usePersistedAuthFlow.js` — `PHONE_SIGNUP` mode added; OTP-verify persistence already covers this flow.
- [x] i18n strings added in `en.js`, `hi.js`, `mr.js` (continueWithPhone(+Sub), phoneSignupTitle/Subtitle/Info,
      fullNameRequired, fullNameTooLong). Existing `fullName` / `fullNamePlaceholder` / `phoneAlreadyRegistered`
      keys reused as-is (single source of truth).
- [x] Style match: orange brand accent (`#f67c16`) on title; phone card uses blue (`#2563EB`) to differentiate
      from Manual (orange), Google (rainbow), Apple (black).
- [x] Lint baseline confirmed: no new categories of errors introduced (the 2 new useCallback
      exhaustive-deps warnings in `UserAuthScreen` follow the same pre-existing pattern the project tolerates;
      i18n dupe-keys flagged are pre-existing on HEAD).
- [ ] **DEFERRED — needs device:** Metro/iOS/Android smoke (open the app, register flow, tap phone card,
      enter name+phone, get OTP from server log / SMS, verify, confirm landing on UserHome). Reserved for the user.

Committed on branch `feature/phone-signup-users-ui` as `fbafaed` (11 files, +503/-10).

## 7. Phase 3 — Add-email-later + verification (phone-only USERS)  `[ ]`
> **Now OPTIONAL / nice-to-have.** Bookings require **phone-verified only** (email no longer gates bookings),
> so phone-only users can book immediately. Email is for recovery/notifications. **Phase 3 can ship AFTER phase 1–2.**
> Confirmed today: `UpdateProfileRequest` has only `fullName` + `phoneNumber` — there is **no email field**, so
> "add email later" is genuinely new. Current email verification = a **link** emailed to the user (not OTP).
- [x] **DECISION (2026-06-22): use the existing email LINK** (`/api/auth/email/verify?token`), NOT OTP.
      Email is **updatable** but verification is **optional** (does not gate bookings).
- [x] **CODED 2026-06-24 (compiles/lints clean; not yet deployed/tested on dev).** Design refined from the
      original note: a **dedicated `SetEmailRequest` + `POST /api/users/email`** (cleaner than overloading
      `UpdateProfileRequest`). **Email CHANGE is supported** and re-triggers verification (per owner). **Dual-DB
      sync:** the email VALUE lands in BOTH DBs; the **verified flag is NOT stored in Mongo for users — read from
      JAuth** (owner decision; Mongo user schema has no such field).
      - **JAuth:** `dto/SetEmailRequest`; `UserService.setEmail(userId, email)` — set-or-change, strict uniqueness
        across active users (matches Mongo sparse-unique so they can't disagree), `isEmailVerified=false`, same-email
        re-submit just resends (rate-limited), already-verified-same-email rejected; `EmailVerificationService`
        refactored with `sendVerificationOnEmailSet` (no resend-rate-limit, invalidates OLD tokens so a stale link
        can't verify a new address) + shared `generateAndSend`; `UserController POST /api/users/email`.
      - **NoeFix:** `authServiceClient.setEmailInJavaAuth`; `userController.addUserEmail` (Mongo uniqueness pre-check
        → JAuth FIRST → mirror email into Mongo; E11000/409/429 mapped; Mongo-fail-after-JAuth returns
        `EMAIL_SET_SYNC_PENDING` and relies on login self-heal); route `POST /api/user/email/:userId`.
      - **RenFi:** `api.js ADD_EMAIL`; `profileService.addUserEmail`; `VerificationScreen` email branch rewired to
        the single NoeFix call (was two silent no-ops); success → reuse `emailSent` ("check inbox"); link auto-sent.
- [ ] **Scope guard upheld:** USER-only path; providers + their `emailVerified` untouched. Changing an email
      re-verifies. (Note: for email/login users this also changes their login email — accepted.)
- [ ] **NOT YET:** deploy to dev (push jauth + noefix feature branches → redeploy) and run the **dual-DB e2e**
      (set email → confirm value in dev Postgres AND dev Mongo, `isEmailVerified=false`; click link → JAuth flips
      verified true; `/users/me` reflects it; change email → re-verify; duplicate → 409). Minor: 2 error strings in
      `VerificationScreen` are hardcoded English (pre-existing pattern) — localize later.

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
- **Phase 1 (JAuth, 6 new files + 1 edit):** `PhoneSignupOtp` entity, `PhoneSignupOtpRepository`,
  `PhoneSignupSendOtpRequest` DTO, `PhoneSignupVerifyRequest` DTO, `PhoneSignupService`, `PhoneSignupController`
  (USER-only — role hard-coded); `SecurityConfig` allowlist +2 entries.
  **(NoeFix, 5 edits):** `utils/profileSync.js` (null-email safe), `utils/authServiceClient.js` (+2 wrappers),
  `controllers/authController.js` (+2 controllers), `routes/authRoutes.js` (+2 routes), `models/user.js`
  (`email: sparse:true` instead of `required:true`). *(provider model + authMiddleware guard confirmed NOT needed.)*
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
- 2026-06-22 — **Phase 0 FUNCTIONAL TEST: GREEN on Mac (dev/H2, EMAIL_PROVIDER=stub, SMS_PROVIDER=stub).**
  App booted on :8080 (`Started AuthServiceApplication in 6.335s`). All 6 §0 endpoint calls returned their
  expected success: (1) register USER → 201 (userId=1, JWT `sub`=email, claim `userId`=1, `role`=USER);
  (2) `/api/users/me` → 200 with correct profile **(confirms identity resolves via `userId` claim, not `sub`)**;
  (3) profile update → 200; (4) `/api/auth/sessions` → 200 (empty); (5) email/password login → 200 with fresh
  tokens reflecting the renamed profile; (6) `/api/auth/email/send-verification` → 200 stub. No 401/500/stacks
  in the regression-path log lines. Phase 0 marked `[x]`. Items still uncheckable on this box (legacy-token
  fallback, NoeFix cross-repo, phone-only login, full provider regression) deferred to Phase 1 acceptance —
  filter contract is unchanged from their POV (subject = email; `userId` claim is what's resolved).
- 2026-06-22 — **Proceeding to Phase 1 pre-flight (§5).** No code edits yet; reading `OtpLoginService`,
  `PhoneOtp`, `SecurityConfig`, NoeFix `authServiceClient` / `authController` / `authRoutes` to map blast
  radius and confirm the empty-email-as-NULL invariant + the on-verify-only account-creation point.
- 2026-06-22 — **Phase 1 PRE-FLIGHT findings (no code changed yet). FLAGGED to user.**
  JAuth side (matches plan):
    - `PhoneOtp.user_id` IS NOT NULL → separate `PhoneSignupOtp` justified.
    - `SecurityConfig` already permits `/api/auth/login/phone/*`; need 2 new public entries
      `/api/auth/signup/phone/send-otp` + `/api/auth/signup/phone/verify`.
    - Reusable infra confirmed: `UserRepository.existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue`,
      `findByPhoneNumberAndIsActiveTrue`, `User.normalizePhoneNumber`, `SmsService`, `OtpLoginService` OTP/rate-limit pattern.
  NoeFix side (BIGGER blast radius than plan documented — `profileSync.js` was NOT in §5/§9 file list):
    1. `utils/profileSync.js:246-247` — `ensureProfileFromJavaAuth` THROWS when `java.email` is falsy
       (`'Java Auth user has no email'`). Phone-only users → null email → throw → no Mongo profile created. **Hard blocker.**
    2. `utils/profileSync.js:98` (`normalizeJavaUser`) — `email: (javaUser.email || '').trim().toLowerCase()`
       silently coerces null to `""`. **Directly violates the §1 NULL-not-empty-string rule** (sparse unique would collide on `""`).
    3. `utils/profileSync.js:263` — existing-doc lookup includes `{ email: java.email }`; with null becomes
       `{ email: null }` and could mass-match other phone-only docs. Must drop the email branch when null.
    4. `models/user.js:77` — `email: { type: String, unique: true, required: true }` → switch to `sparse` (and drop `required:true`).
  Recommendation: add `utils/profileSync.js` to Phase 1 file-change list (§5/§9). Smallest safe edits — change `throw`
  to `if (java.email) {...}` branches, propagate null through `identitySet`, skip the email branch in the `$or` lookup
  when null. Existing email-having users (manual register / Google / Apple) preserve current behaviour. No code edited
  until the user acks. App stopped on :8080.
- 2026-06-22 — **User acked Phase 1 go-ahead.** JAuth half coded (6 new files: `PhoneSignupOtp` entity, repo,
  `PhoneSignupSendOtpRequest` + `PhoneSignupVerifyRequest` DTOs, `PhoneSignupService`, `PhoneSignupController`)
  + `SecurityConfig` allowlist updated (+2 entries). `./mvnw clean compile` → BUILD SUCCESS, 110 files (was 104).
- 2026-06-23 — **Phase 1 FULL e2e GREEN.** Installed `mongodb-memory-server` (v11.2.0) as NoeFix devDependency,
  wrote `/tmp/phase1_full_e2e.js`, restarted local JAuth (dev/H2/stub-SMS), and ran 5 scenarios end-to-end through
  the new NoeFix wrappers + `ensureProfileFromJavaAuth` against an in-process Mongo. **22 assertions PASSED:**
  (A) phone-only signup → Mongo doc with `email: undefined` (NOT `""`), 10-digit phone, unified `_id`,
  business-field overrides persisted; (B) classic email register regression → email lowercased+trimmed onto doc;
  (C) re-syncing the same JAuth user is idempotent (no duplicate insert); (D) phone-only user later gaining email
  patches onto existing doc (Phase 3 forward-compat); (E) two phone-only users with no email both insert under a
  manually-built sparse unique email index (proves the §4 prod index-rebuild step actually achieves coexistence).
  Side findings (not in scope to fix): User schema has no `phoneVerified` field (JAuth is authoritative for
  verification state); pre-existing `{phone:1}` index mixes `sparse+partialFilterExpression` which Mongo refuses
  on `syncIndexes` — assumed prod runs with `autoIndex:false`. JAuth stopped, in-memory Mongo torn down cleanly.
- 2026-06-23 — **NoeFix Phase 1 code DONE (5 files edited).** `models/user.js` email switched to sparse unique;
  `utils/profileSync.js` made null-email safe (no more coercion, no more throw, no more `{email:null}` lookup);
  `utils/authServiceClient.js` got 2 new wrappers; `controllers/authController.js` got 2 new controllers
  (`requestPhoneSignupOtp`, `verifyPhoneSignupAndSync`) modelled on `register()` with full legal-acceptance gate
  + defensive `role !== USER` check; `routes/authRoutes.js` got 2 new public-rate-limited routes.
  All 5 files pass `node --check`. **JAuth-integration smoke test green:** `/tmp/phase1_wrapper_test.js` calls
  the new wrappers against local JAuth → user #3 created (`email=null, role=USER, isPhoneVerified=true`); duplicate
  re-send → 409 ALREADY_REGISTERED with correct mapping. **DEFERRED:** the Mongo-write half of e2e
  (`ensureProfileFromJavaAuth` upsert). This Mac has no `mongod`/Docker, and the NoeFix `.env` points to prod Mongo
  Atlas — explicitly refused per AGENTS.md §3 (no prod data pollution). Phase 1 NoeFix marked `[~]` until a dev
  Mongo is available; full e2e instructions left in §5 Verification block.
- 2026-06-22 — **JAuth Phase 1 functional test GREEN on Mac (dev/H2 / stub SMS).**
  Smoke flow: `POST /api/auth/signup/phone/send-otp` (+91 9876543212, "Phone Only Three") → 200 + maskedPhone;
  read OTP from `StubSmsService` log; `POST /api/auth/signup/phone/verify` → 200 + tokens, `email=null`,
  `isPhoneVerified=true`, `isNewUser=true`. `GET /api/users/me` with the new token → 200, `email=null`,
  `hasPassword=false`. **Duplicate path:** re-send for same phone → 409 `ALREADY_REGISTERED`.
  **Phase 0 critical check unblocked:** `POST /api/auth/login/phone/send-otp` + `/verify` for the phone-only
  user → 200, tokens issued with null subject, `/api/users/me` resolves via `userId` claim. **Regression:**
  classic email/password register works alongside (userId=2 created, hasPassword=true, email set). Phase 0
  §4 verification-checklist items "Existing phone-OTP LOGIN works for an email-less user" now PASSED. JAuth
  half of Phase 1 marked `[x]`. NoeFix half next.
- 2026-06-24 — **Status review session (no code changed).** Confirmed code side is done (Phases 0–2); remaining
  work is GO-LIVE only. Verified from `renfi/src/config/{environment,api}.js` that the app has **NO test/staging
  tier** — strictly PRODUCTION (`api.fixhomi.com`/`auth.fixhomi.com`) or LOCAL (`__DEV__`). Corrected the user's
  proposed "deploy to test → point app at test → test → prod" flow: there is no test cloud to point at, so testing
  is either LOCAL backends or stand-up staging. Affirmed "prod DB migration before backend" as the safe order
  (with the nuance that JAuth boots fine either way; migrations must precede the first null-email INSERT). Added
  **§12 GO-LIVE / DEPLOY CHECKLIST** as the ordered runbook + resume pointer for the next session. Nothing pushed,
  nothing deployed, no migrations applied.

---

- 2026-06-24 — **4-agent independent code audit (Opus 4.8) — ALL GREEN.** Ran 4 parallel auditors against the
  committed diffs (JAuth 3ef879b/c752c96, NoeFix bdc320c, RenFi fbafaed) + current HEAD:
  (1) **Existing-user / legacy-token safety = SAFE** — JWT filter resolves by signed `userId` claim w/ email
  fallback; subject still = email; ALL `getName()` consumers parse Long (exhaustive); no logout/revocation; claim
  is signature-bound (not spoofable). All real issued tokens carry `userId` since the first JWT commit → none break.
  (2) **JAuth signup parity = OK** — same `User` entity, same `UserRepository.save`, same `users` table + auditing;
  differs only in intended ways (email=null, password=null, isPhoneVerified=true) + role hard-locked USER.
  (3) **NoeFix sync parity + existing-user safety = OK** — same `ensureProfileFromJavaAuth(role='user')`, unified
  `_id===javaUserId`, null email stored ABSENT (not ""), `$or` drops `{email:null}`, idempotent, legal-gated.
  (4) **Providers untouched = YES; abuse-resistant = YES** — no provider file in any commit; role un-overridable;
  RenFi card ungated for providers; OTP SecureRandom 6-digit, 3-attempt lockout, 5-min expiry, per-phone 3/min cap.
  Non-blocking: pre-existing spoofable `X-Forwarded-For` IP limiter (per-phone cap mitigates); the 2 manual DB
  migrations are the only real operational risk and fail CLOSED if skipped. No code changed during the audit.

- 2026-06-24 — **[!] INCIDENT / FLAG: "dev" Render env shares PROD databases.** Deployed phone-signup branches to
  `jauth-dev.onrender.com` + `noefix-dev.onrender.com` (both healthy: JAuth `/actuator/health`→200, NoeFix `/`→200).
  **Then discovered both dev services point at the REAL PROD Postgres + Mongo, and dev uses REAL SMS.** Assessed
  blast radius from config (no code run by us):
  - JAuth `ddl-auto: update` (application.yaml:26 + application-prod.yaml:45) → booting dev against prod Postgres
    **created the new `phone_signup_otps` table in PROD** (additive, harmless, needed at go-live anyway). The email
    nullable change did NOT apply (update mode never drops NOT NULL) → prod `email` still NOT NULL. No existing
    column/constraint/row altered.
  - NoeFix Mongo (`config/db.js`, Mongoose default autoIndex:true, no syncIndexes) → tried to create the sparse
    unique email index; prod's existing non-sparse unique index causes an IndexOptionsConflict that Mongoose LOGS
    and IGNORES — **no index dropped/rebuilt on prod**. Prod email index intact.
  - **Conclusion: NO destructive change to prod, existing users unaffected.** But the env has NO isolation.
  **STOPPED before running any signup smoke** (would create real users in prod + send real SMS). **Required fix:**
  give the dev services SEPARATE dev databases (dev Postgres e.g. Neon branch + dev Mongo), repoint env vars,
  redeploy (fresh schema auto-builds correctly, no migration needed on dev), THEN test. Until then: do not point
  the app at the dev URLs, do not hit the dev signup endpoint; ideally suspend the two dev services.
- 2026-06-24 — **ISOLATED DEV ENV STOOD UP + FULL BACKEND E2E GREEN on real infra.** Resolution of the incident
  above: gave the dev services their OWN databases — dev Postgres = a **Neon branch off prod** (`dev-phone-signup`,
  copy of prod data), dev Mongo = a **fresh Atlas cluster** (`fixhomi-dev`), a **separate dev JWT secret** (shared
  between jauth-dev + noefix-dev), cross-URLs repointed (noefix-dev→jauth-dev, jauth-dev→noefix-dev), real MSG91 SMS.
  - **Isolation PROVEN:** registered a throwaway email user on noefix-dev → 201; same creds on PROD (`api.fixhomi.com`)
    → 401 (absent) → **dev writes do NOT reach prod.**
  - **Found feature code was never pushed:** jauth `c752c96` + noefix `bdc320c` were local-only → dev had deployed
    stale (Phase-0-only) code → `/signup/phone/*` 404'd. **Pushed both feature branches → redeployed → routes live.**
  - **MIGRATION SIMPLIFIED (verified on the branch):** the ONLY required prod Postgres change is
    **`ALTER TABLE users ALTER COLUMN email DROP NOT NULL;`** — `idx_email` is a standard UNIQUE constraint that
    already allows multiple NULLs, so the partial-index DROP/CREATE from the old §4 plan is **unnecessary** (and
    riskier). Discovery on the prod-copy branch: email was `NOT NULL`; single email enforcer `idx_email`; **0
    empty-string emails, 0 duplicate emails** → prod migrates cleanly. Ran the one-liner on the branch → `email`
    nullable = YES.
  - **PHONE SIGNUP E2E GREEN:** real OTP to a real phone → `verify` → **201**, JAuth user `1762` (`email=null`,
    `phone=9146282497`, `isPhoneVerified=true`, `hasPassword=false`), unified Mongo profile `_id=1762`, JWT with no
    `sub`. `GET /api/users/me` with that null-subject token → **200** (resolves via `userId` claim). Duplicate number
    → 409 `PHONE_ALREADY_EXISTS` (also confirmed). Backend feature fully validated on staging infra.
  - **Still untested:** the RenFi **app UI** leg (point app at dev URLs + on-device flow), then the PROD rollout.
- 2026-06-24 — **RenFi wired to dev + Phase 2 UI audited & polished (3 Opus reviewers).** Added a
  `USE_DEV_STAGING` flag in `renfi/src/config/environment.js` (redirects the app to jauth-dev/noefix-dev; loud
  startup warning; MUST be false for prod release — do not commit true). Audit results: **theme/colors consistent**
  (phone card blue `#2563EB`, reuses user-auth card anatomy; provider flow provably untouched via the `onPickPhone`
  gate; note: in this app auth screens are brand-orange for BOTH roles — the user/provider color split lives in the
  home screens, and `OTPVerifyScreen` is already shared by both logins); **i18n complete** in en/hi/mr (fixed one
  hardcoded `accessibilityLabel="Go back"` → `t('common.goBack')`). **OTP screen polished** (shared with live phone
  LOGIN — login must be re-tested): autofocus on mount, SMS autofill (iOS `oneTimeCode` / Android `sms-otp`),
  active-cell highlight with caret hidden (no flicker), keyboard-dismiss on success, double-submit ref guard,
  Android backspace fallback, ScrollView wrap (no clipping under keyboard), role-string normalization (dropped a
  `'user'` literal). All changes lint-clean. **Wrote `renfi/renfi/PHONE_SIGNUP_TEST_PLAN.md`** — full iOS+Android
  device test matrix (signup, OTP behavior, MANDATORY login regression, provider-untouched, theme, i18n, null-email
  usage, pre-release checklist). Next: self-run the test plan on a device → optional dev-pointed tester round →
  PROD rollout (§12) → prod release build with `USE_DEV_STAGING=false`.

- 2026-06-24 — **RenFi UI changes committed (`feature/phone-signup-users-ui` @ `b1851b4`, 4 files) + DEV-POINTED
  TESTER BUILD going out.** Committed with `USE_DEV_STAGING=false` in git (clean); for the tester build the flag is
  flipped to `true` locally (uncommitted) so the app hits jauth-dev/noefix-dev. Distributing via Firebase App
  Distribution; testers create accounts in the dev DB (owner OK'd new-account creation; real MSG91 SMS; dev Render
  may cold-start). **Did NOT commit two pre-existing native version bumps** (android versionCode 22→26 fine; **iOS
  `CURRENT_PROJECT_VERSION` 14→1 is a REGRESSION — must be fixed > last App Store build before store submission**).
  **Status: awaiting tester feedback.** Go-live runbook captured in the §0 RESUME block + §12 below. After build,
  restore the flag with `git checkout renfi/src/config/environment.js`.

## 12. GO-LIVE / DEPLOY CHECKLIST  `[ ]`  ← **START HERE NEXT SESSION**

**The code is done (Phases 0–2). This section is the remaining path to production.**
Everything below is deploy/test/release — there is **no more feature coding** required (Phase 3
"add-email-later" is OPTIONAL and explicitly NOT part of go-live).

### ⚠️ Key facts that shape the order (verified 2026-06-24)
- **The RenFi app has NO "test"/staging tier.** `renfi/src/config/environment.js` + `config/api.js`
  are strictly binary: **PRODUCTION** (`api.fixhomi.com` / `auth.fixhomi.com`) **or LOCAL**
  (`__DEV__` → `localhost:5001` / `:8080`). There is no `test.fixhomi.com`. So "test the build"
  means EITHER (A) point the app at **local** backends, OR (B) stand up real staging infra first.
- **App talks to backends, not DBs directly:** RenFi → NoeFix (`api.fixhomi.com`) → JAuth
  (`auth.fixhomi.com`) → Postgres + Mongo. So backends MUST be updated before the app can be tested
  against prod — and the prod DB migrations gate the backends.
- **Migration timing:** JAuth boots fine before the migration (Hibernate `ddl-auto:update` only adds,
  never drops the existing `NOT NULL`). The migrations must be done **before the first real
  phone-signup INSERT** (null email in Postgres / null email in Mongo). Since signup traffic only
  arrives once the app ships, "migration first, then deploy backend" is the clean, safe rule.

### Step 0 — Pre-deploy housekeeping  `[ ]`
- [ ] **Branch bases.** JAuth `feature/phone-signup-users` was cut off `feature/apple_devlopment`
      (NOT `main`); RenFi off `backgroun-tracking-update-fe`; NoeFix `phone-signup-providers`.
      Confirm what each Render service actually DEPLOYS FROM, and decide the merge target per repo.
- [ ] **Push the local commits** in all 3 repos (currently nothing is pushed).
- [ ] ⚠️ Confirm none of the diffs accidentally flips `USE_PRODUCTION_*` to `false` (a local-test
      toggle must never be committed).

### Step 1 — Test the feature (pick ONE)  `[ ]`
- [ ] **Option A — LOCAL (recommended first pass, zero prod risk):**
      set `USE_PRODUCTION_NODE_API=false` + `USE_PRODUCTION_JAVA_AUTH=false` in `environment.js`,
      run JAuth (H2) + NoeFix (local/dev Mongo) on the Mac, build a dev app, run the full
      phone-signup flow on a real device (choice screen → name+phone → OTP → land on UserHome).
      **Do NOT commit the `false` toggles.**
- [ ] **Option B — STAGING (faithful, but real setup cost):** spin up throwaway Render services +
      a staging Postgres + staging Mongo, add a staging URL to the config, point the app at it.
- [ ] Provider regression smoke during whichever option: provider manual/Google/phone-login flows
      must look and behave identically (the §6 gating prop should make the provider choice screen unchanged).

### Step 2 — Prod DB migrations (MANUAL, before deploying backends)  `[ ]`
> ⚠️ Touches LIVE tables. Additive/relaxing only (no row rewritten), but irreversible-in-practice.
> Take a Postgres snapshot / Mongo backup first. Needs explicit go-ahead at run time.
- [ ] **Postgres (JAuth DB) — ONE LINE (verified on the dev prod-copy branch 2026-06-24):**
      ```sql
      ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
      ```
      That's the ONLY required change. Do NOT drop/recreate `idx_email` — it's a standard UNIQUE constraint that
      already allows multiple NULLs, so the old partial-index rebuild is unnecessary and riskier. Prod data was
      confirmed clean (0 empty-string, 0 duplicate emails) so this migrates without issue.
- [ ] **Mongo (NoeFix DB):** rebuild the `email` index as **sparse unique** (drop old non-sparse
      unique, recreate `{ email: 1 }, { unique: true, sparse: true }`). Verify prod runs `autoIndex:false`
      (the tracker assumed it; confirm before relying on manual index management).

### Step 3 — Deploy backends to prod  `[ ]`
- [ ] Deploy **JAuth** (Phase 0 + 1) to Render. Verify boot + a non-phone smoke (email login →
      `/api/users/me`) so existing users are provably unaffected.
- [ ] Deploy **NoeFix** (Phase 1) to Render. Verify a non-phone smoke (existing user profile sync).
- [ ] Prod smoke of the new endpoints with a real throwaway phone:
      `POST /api/auth/signup/phone/send-otp` → `/verify` → confirm a phone-only user is created
      (`email=null`) in BOTH Postgres and Mongo, and existing email users still work.

### Step 4 — Build + distribute the app  `[ ]`
- [ ] Ensure `USE_PRODUCTION_*` are BOTH `true` (prod) for the release build.
- [ ] Build RenFi (iOS + Android), push to **Firebase App Distribution**.
- [ ] Real-device smoke against PROD: full phone-signup → confirm landing + that the user can book.
- [ ] ⚠️ Note: testing against prod creates real (test) phone-only users in the prod DBs — plan to
      identify/clean them, or use a disposable number.

### Step 5 — Release  `[ ]`
- [ ] If the Firebase test is clean → submit the app version to **Play Store** + **App Store**.
- [ ] Backends are already live from Step 3, so "release" here is the app stores only.
- [ ] Post-release: watch the Phase 4 Crashlytics `ForcedLogoutNonFatal` / signup funnel for regressions.

### (Deferred, not blocking go-live)
- [ ] **Phase 3 — add-email-later** (§7). Coded 2026-06-24; deploy to dev + e2e, then it can ship.

> 🔒 **§13 PRE-PROD SECURITY TODO is a hard gate before any prod deploy.** Dev tester round does not require it.

---

## 13. PRE-PROD SECURITY TODO  `[ ]`  (from 3-agent adversarial review, 2026-06-24)

**Verdict: no auth-bypass / IDOR / account-takeover / token-forgery / role-escalation / secret-leak / injection
found — all confirmed mitigated.** Findings below are abuse/cost + hygiene; safe for a trusted DEV tester round,
but **fix the HIGH + MED items before PROD**. Decide each before release.

**HIGH (fix before prod)**
- [ ] **H1 — verification-email bombing.** `UserService.setEmail` → `sendVerificationOnEmailSet` deliberately skips
      the resend rate-limit, and neither `POST /api/user/email/:userId` (NoeFix, `routes/userRoutes.js`) nor
      `POST /api/users/email` (JAuth) is route-limited → an authed user can send ~200 verification mails/min to
      arbitrary addresses. **Fix:** add `strictRateLimiter` (15/min, exists) to the NoeFix route AND throttle by
      DESTINATION email in `EmailVerificationService.generateAndSend` (count recent sends to `user.getEmail()`, not
      by userId — alternating addresses defeats the userId cap).
- [ ] **H2 — phone-signup OTP in wrong rate-limit bucket.** `RateLimitingFilter.isOtpEndpoint()` matches `"/otp"`
      but the path is `…/signup/phone/send-otp` (substring `-otp`) → it gets the 100/min general bucket, not the
      5/min OTP bucket → SMS-cost abuse (attacker hits JAuth directly, bypassing NoeFix's 30/min). **Fix:** match
      `send-otp` / `/signup/` (and route `…/verify` into the OTP bucket too). Consider a global SMS budget/circuit-breaker.

**MEDIUM (fix before prod)**
- [ ] **M1 — verifyEmail trusts current email, not the token's bound email.** `EmailVerificationService.verifyEmail`
      sets `isEmailVerified=true` without comparing `verificationToken.getEmail()` to `user.getEmail()`. Currently
      safe (every set/change invalidates old tokens), but no 2nd line of defense. **Fix:** reject if
      `!token.getEmail().equalsIgnoreCase(user.getEmail())`.
- [ ] **M2 — PII in logs.** Full email logged at INFO/WARN: `EmailVerificationService` (~:167, :196), `UserService`
      (~:445, :605, :624 — logs raw email+phone while anonymizing); verify-token prefix logged (~:157). **Fix:**
      route through existing `maskEmail()`/`maskPhone()`; drop the token-prefix log.

**LOW / robustness / risk-accept**
- [ ] **L1** — phone-reclaim race: map `DataIntegrityViolationException` (idx_phone) to a clean 409 `ALREADY_REGISTERED`
      (`PhoneSignupService` verify-time insert) instead of a generic 500.
- [ ] **L2** — set-email 409 is an email-enumeration oracle (inherent to change-email UX; the H1 limiter caps speed).
- [ ] **L3** — `VerificationController.buildSuccessHtml`: HTML-escape + URL-encode the reflected email (parity with
      the error branch). Not reachable today (input validated), hygiene only.
- [ ] **L4** — add a `@PostConstruct` assertion that `JWT_SECRET` ≥ 64 bytes (fail fast); normalize the `userId`
      claim read to `Number.class).longValue()` to avoid spurious 401s.
- [ ] **L5 (risk-accept?)** — refresh-token lifetime 60 days; confirm rotation invalidates the prior token.
- [ ] **GATE** — `renfi/src/config/environment.js` `USE_DEV_STAGING` MUST be `false` for the prod build (git is
      already false; never commit `true`).

**Confirmed SAFE (no action):** JWT signature-first resolution (no claim/header injection, no alg-confusion),
IDOR locked via `requireParamOwnership` bound to the token, account-takeover blocked (strict uniqueness + reset on
change + token invalidation), role hard-locked USER, OTP `SecureRandom` 6-digit + 3-attempt lockout + constant-time
compare + single-use, no committed secrets, scoped `$set` (no mass-assignment), Mongo input string-normalized,
CORS explicit-origins only.

---

## 14. UNCOMMITTED — coded, NOT pushed (push later)  `[x]` COMMITTED + PUSHED 2026-07-08 (jauth `f5f7aac`; tradeoff accepted — email-OTP reset now reveals Google accounts, consistent with the phone path)

### 14.1 Forgot-password-via-EMAIL: no feedback for Google accounts + missing i18n key (2026-06-29)
**Bug:** forgot-password → "via email" → Send OTP did nothing for a **Google (OAuth) account** (no password to
reset). The PHONE path throws a clear "Google Sign-In" error the UI shows; the EMAIL path silently returned generic
success → no OTP, no explanation → then verify said "No pending password reset." Also the `auth.otpSentEmail` i18n
key was missing (UI fell back to hardcoded English; hi/mr showed English).

**Fix (coded, lint/compile clean, NOT committed):**
- JAuth `service/PasswordResetService.java` — `requestPasswordResetEmailOtp` now **throws**
  `AuthenticationException("This account uses Google Sign-In…")` for OAuth-only accounts (was a silent return).
  Password accounts unchanged (still send OTP); non-existent emails still silent (anti-enumeration preserved).
- JAuth `controller/VerificationController.java` — `forgotPasswordEmail` catches it → `400 GOOGLE_ACCOUNT` + message;
  the RenFi screen's existing `errorMsg.includes('Google Sign-In')` handler then shows the proper alert.
- RenFi `i18n/en.js|hi.js|mr.js` — added `auth.otpSentEmail`.

**⚠️ Tradeoff to confirm before pushing:** the email reset now REVEALS when an email is a registered Google
account (was silent). The phone path already does this, so it's consistent; non-existent emails stay hidden. If
strict anti-enumeration on email is preferred, revert the two JAuth changes and keep only the i18n key.

**To take effect after push:** redeploy `jauth-dev` (backend) + new app build (i18n). Files to commit:
`PasswordResetService.java`, `VerificationController.java` (JAuth `feature/phone-signup-users`);
`i18n/{en,hi,mr}.js` (RenFi `feature/phone-signup-users-ui`).

---

## 15. UNIFIED USER AUTH — one-tap login/register (2026-07-08)  `[x]` CODED + COMMITTED + PUSHED + DEV-DEPLOYED

> **NEXT-SESSION ENTRY POINT.** This section is the source of truth for the unified-auth work.
> Everything below is committed & pushed and live on the dev Render instances against fresh
> prod-snapshot databases. Remaining: on-device smoke test → Firebase App Distribution.

### 15.1 What it is (product behavior)
USER (customer) flow ONLY — providers 100% untouched. Login/Register merged into ONE screen:
- **UnifiedUserAuthScreen**: Continue with Google (both platforms) / Apple (iOS only, App Store 4.8) /
  Phone OTP. Terms sentence with tappable T&C/Privacy links UNDER the buttons (acceptance recorded at
  sign-in, `termsAccepted:true` sent with auth). NO email/password sign-in for users anymore
  (existing password users must use Google same-email auto-link or phone OTP — accepted).
- Existing account → logs straight in. Unknown account → auto-registered (`isNewUser` in response).
- **WelcomeModal** (new users only): "Welcome to FixHomi" + terms sentence + OPTIONAL name input
  (phone signups only — Google/Apple bring a name; min 2 chars to match Node validation) + OPTIONAL
  referral code (pre-filled from `pendingReferralCode` deep link; cleared only after successful apply
  or explicit skip). Skip always available. Referral/name failures NEVER block login.
- Phone accounts are created with an **EMPTY name** (never a placeholder like "User").
- **Booking gate** (name + verified phone required to CREATE any service request): enforced
  server-side (403 `PROFILE_INCOMPLETE` + `missing:{name,phone,phoneVerified}`) on traditional/event/
  emergency create handlers, AND client-side on all 5 booking screens with a "Complete your profile"
  dialog → navigates to `Profile` (existing name-edit + phone add/verify UI). Fail-OPEN when Java Auth
  unreachable (data gate, not security). Changing profile phone RESETS phoneVerified/verifiedPhone.

### 15.2 Commits (all pushed)
- **jauth** `feature/phone-signup-users`: `f5f7aac` (§14 reset fix), `60e58fa` phone-signup fullName
  optional (blank → stored `""`; `User.fullName` @NotBlank→@Size(max=100), column stays NOT NULL —
  no migration; DTO field kept for old builds). `isNewUser` was ALREADY in LoginResponse for
  Google/Apple/phone — no change needed.
- **noefix** `phone-signup-providers`: `2eedecb` — unified OTP + apply-code + booking gate (below).
- **renfi** `feature/phone-signup-users-ui`: `56ece45` — all app changes (below).

### 15.3 New/changed API contracts (NoeFix)
- `POST /api/auth/phone/unified/send-otp` {phoneNumber} → 200 {success, flow:'login'|'signup',
  maskedPhone, expiresInMinutes} | 409 ROLE_CONFLICT (provider-owned) | 429. **strictRateLimiter**
  (15/min/IP — SMS cost + existence oracle). Cross-DB drift fallbacks: signup 409→retry login,
  login 404→fallback signup.
- `POST /api/auth/phone/unified/verify` {phoneNumber, otp, flow, termsAccepted, privacyAccepted,
  referralCode?} → tokens + isNewUser. signup=executePhoneSignupVerify (extracted core of legacy
  verify; legalAcceptance write NON-FATAL); login=Java verify + role guard (non-USER → 403
  ROLE_CONFLICT + **revokes the just-minted Java refresh token**) + Mongo self-heal.
- `POST /api/referral/apply-code` {code} (authenticateToken + strictRateLimiter) → wraps
  processReferral; self-heals missing Mongo profile from Java Auth before applying. Errors:
  INVALID_CODE/SELF_REFERRAL 400, ALREADY_REFERRED 409, DAILY_LIMIT 429. processReferral now returns
  {applied, reason,...}; daily cap counts ONLY credited referrals (was: burnable by no-op calls).
- Google/Apple exchange (JAuth, existing): client now sends **NO `mode`** → auto login-or-register.
- Mongo `User` schema now declares `phoneVerified`/`verifiedPhone`/`isEmailVerified` (writes were
  silently dropped by strict mode before). `updateUserById` phone change resets verification.

### 15.4 File map (for next session — read these, ignore the rest)
**renfi** (`src/`): screens/UnifiedUserAuthScreen.jsx (NEW), screens/PhoneNumberScreen.jsx (NEW,
number-only entry), components/WelcomeModal.jsx (NEW), hooks/useBookingProfileGate.js (NEW),
screens/UserAuthScreen.jsx (rewritten: UNIFIED/PHONE_INPUT/OTP_VERIFY modes, popup orchestration,
pendingAuth holds tokens until popup done, updateNameWithToken → Java PUT /api/users/profile
{fullName} + Node PUT /api/user/profile/:id {fullName}), screens/OTPVerifyScreen.jsx (additive
context==='unified' branch + PROFILE_SYNC_FAILED/MONGODB_SYNC_FAILED/PHONE_ALREADY_EXISTS cases),
hooks/usePersistedAuthFlow.js (UNIFIED+PHONE_INPUT modes added; provider default unchanged),
services/{authService,googleAuthService,appleAuthService,referralService}.js, config/api.js
(OTP_UNIFIED + REFERRAL.APPLY_CODE), i18n/{en,hi,mr}.js, booking-gate wiring in screens/{UserHome,
CreateServiceRequest,EmergencyServices,EventServices,Favorites}Screen.jsx. PhoneSignupScreen.jsx
deprecated (kept for reference). LoginScreen/RegisterScreen/OTPLoginScreen still used by PROVIDERS.
**noefix**: controllers/authController.js (requestUnifiedPhoneOtp, verifyUnifiedPhoneOtp,
executePhoneSignupVerify, lookupVerifiedPhoneAccount), controllers/referralController.js (applyCode,
processReferral refactor, isDailyLimitReached/recordDailyReferral split), controllers/userController.js
(phone-change verification reset), utils/serviceHelpers.js (ensureBookingProfileComplete),
utils/authServiceClient.js (sendPhoneLoginOtp/verifyPhoneLoginOtp/revokeJavaAuthRefreshToken),
models/user.js, routes/{authRoutes,referralRoutes}.js, 3 service controllers (gate wiring).
**jauth**: PhoneSignupService/Controller, PhoneSignupSendOtpRequest, entity/User.java.

### 15.5 Reviews run before commit (3 read-only agents) + fixes applied
Security (no CRITICAL/HIGH): daily-cap burn fix, apply-code+unified-send strict rate limits, sticky
phoneVerified reset on phone change, gate prefers req.auth.userId, ROLE_CONFLICT token revoke.
Resilience: legalAcceptance write non-fatal; apply-code profile self-heal; gate fail-open on Java
outage; name min-2 enforced in modal (Node rejects <2 → divergence). UX: Apple glyph was EMPTY
STRING (fixed ); booking gate also runs at CreateServiceRequestScreen mount (form-loss);
submittingRef double-tap guards (WelcomeModal/PhoneNumberScreen); persisted OTP step cleared when
popup opens (app-kill restore bug); deep-link code survives transient failures; SafeArea bottom edge.
Known-accepted: Android hardware-back on popup = skip+login (account already exists); consumed-OTP
retry needs a resend (messaged in-UI); in-memory rate/daily limits per-instance (MVP).

### 15.6 Dev infra state (2026-07-08)
- **Neon**: NEW branch `dev-unified-auth` off production (prod project, console.neon.tech). 1983
  users. `users.email` NULLABLE=YES (manual ALTER run — Hibernate never drops constraints). All new
  tables auto-created by ddl-auto (phone_signup_otps/phone_otps/email_otps/refresh_tokens verified).
  jauth-dev Render env DATABASE_HOST/NAME/USERNAME/PASSWORD → this branch. (Old dev DB expired —
  branch TTL; leave expiry EMPTY on future branches.)
- **Mongo dev (Flex)**: fresh prod mongodump/mongorestore --drop. `users.email` index rebuilt
  unique+sparse (CRITICAL for phone-only null emails). fcmToken CLEARED (users 1187, providers 626
  modified) so testers can't push-notify real prod users.
- **Render dev**: jauth-dev + noefix-dev deployed from the pushed branches; verified live via probes
  (unified endpoint 400 MISSING_PHONE_NUMBER; jauth validation no longer requires fullName;
  apply-code 401 without token).
- **App**: environment.js `USE_DEV_STAGING=true` (UNCOMMITTED, correct for tester build; FLIP TO
  false BEFORE ANY PROD RELEASE). android versionCode 28 / versionName 1.0.5 (uncommitted).

### 15.7 Remaining / next steps  `[ ]`
- [ ] Build `./gradlew assembleRelease` → Firebase App Distribution → on-device smoke test:
      Google new+existing, phone new+existing, provider-phone conflict, provider regression,
      sign-out/in (no popup), referral +50/+50, booking gate (skip name → blocked → fix → works).
      Watch: release-keystore SHA-1 must be in Firebase for Google Sign-In (DEVELOPER_ERROR = missing).
- [ ] §13 pre-prod security TODOs still open (H1 email-bombing, H2 phone-signup OTP rate-limit bucket…).
- [ ] §12 go-live: NOTE — prod Postgres email NOT NULL migration + prod Mongo sparse index rebuild
      STILL REQUIRED at prod deploy time (dev got them via branch-ALTER + restore-reindex).
- [ ] iOS build number regression (pbxproj 14→1) before any TestFlight upload.
- [ ] Product follow-ups accepted for later: T&C 4-month cycles vs code 2-cycles/year discrepancy.
