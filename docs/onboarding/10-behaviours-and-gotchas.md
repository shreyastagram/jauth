# 10 · Behaviours and Gotchas

Things about the current code that surprise people. Each item was reproduced on a local instance or traced in the code (file references relative to `src/main/java/com/fixhomi/auth/`). Knowing them will save you hours of debugging.

---

## HTTP behaviour

**1. Unauthenticated or forbidden → 302, not 401/403.**
`oauth2Login()` is enabled and there is no custom entry point or access-denied handler, so Spring redirects to `/oauth2/authorize/google` (or `/login` for `X-Requested-With: XMLHttpRequest`). Postman and browsers follow the redirect and show Google's sign-in page. To see the real status, turn off redirect-following (the Postman folder 10 requests already do). → [04 §4](04-security-jwt-rbac.md#4-what-a-denied-request-looks-like)

**2. A stale token breaks public endpoints too.**
`JwtAuthenticationFilter` runs on every path. If the `Authorization` header holds a validly signed token for a deleted or disabled user, even `/api/auth/login` returns `401 {"code":"ACCOUNT_DELETED"}`. Send public requests without a token.

**3. Three error shapes.** `ErrorResponse` (global handler), `{success, code, message}` (OTP login, phone signup, forgot-password by phone/email), and raw JSON written by filters (rate limit, `ACCOUNT_DELETED`). → [03](03-api-reference.md#errors-three-shapes)

**4. The same condition can return different statuses.** Examples:
- The OTP send limit is 429 on login/signup but **400** on `/api/auth/otp/send` and `/api/users/phone/change/send-otp`.
- A disabled account is 401 on Google/Apple but 404 `USER_NOT_FOUND` on OTP login.

**5. Malformed input returns 500.** Invalid JSON, an unknown `role` value, a missing required query parameter or a non-numeric path id all reach the catch-all handler.

**6. `GET /api/auth/email/verify` always returns 200 HTML**, even when the token is invalid. Success or failure is in the page and in the `status=` query of its deep link.

**7. Swagger UI and the H2 console are not reachable anonymously.** They are not in the permit list, so you get a 302. Use Postman and the IntelliJ Database tool instead. → [01 §8](01-local-setup.md#8-running-from-intellij-idea)

## Tokens and sessions

**8. Logout does not kill the access token.** Logout, password change, password reset and "revoke session" only revoke refresh tokens. A previously issued access token works until its `exp` (24 h). Only disabling or deleting the user stops it immediately, because the filter checks `is_active` on every request.

**9. A role change takes effect on the next token.** Authorization reads `role` from the JWT. After `UPDATE users SET role=…` the user keeps the old role until they refresh (refresh re-reads the DB role) or log in again.

**10. Sessions are never recorded.** `SessionService.createOrUpdateSession` has no callers, so:
- `GET /api/auth/sessions` is always empty;
- `DELETE /api/auth/sessions/{id}` returns 401 "Session not found";
- `revoke-all` returns `revokedCount: 0`.

Trusted devices (`/api/auth/devices/trust`) are stored and listed, but nothing else reads them.

**11. Two "validate" endpoints disagree on purpose.** `/api/token/validate` reports the role in the **JWT**; `/api/auth/validate` reports the role in the **database**.

## Data and identity

**12. Email lookup is case-sensitive at login.** Emails are stored lowercased, but `AuthService.login` looks up the raw input (`AuthService.java:74`). `Case.Test@example.com` gets 401 while `case.test@example.com` works. The same applies to email-OTP send and forgot-password by link.

**13. Phone numbers are normalised to 10 digits on save** (`User.normalizePhoneNumber`): `+91 98123 45678` → `9812345678`. Lookups normalise too, so send any common format.

**14. "Reclaim" of unverified identifiers.** When someone registers or signs in with an email or phone that an *unverified* account holds, that other account is deactivated (email) or loses the number (phone). Verified identifiers are never taken over; the request gets a 409 instead.

**15. Phone-signup accounts have no email and no password.** `sub` is missing from their JWT, `hasPassword` is false, and phone+password login and forgot-password always fail for them. They log in with phone OTP.

**16. Staff accounts are invisible in the admin list.** `GET /api/admin/users/list` only returns USER and SERVICE_PROVIDER. ADMIN, IT_ADMIN and SUPPORT can be fetched by id but not listed.

**17. Staff cannot use mobile Google/Apple sign-in.** Those endpoints only accept USER/SERVICE_PROVIDER as the requested role, so an existing staff account gets `ROLE_CONFLICT`.

**18. `DELETE /api/users/{userId}` has its own rule.** Allowed for the user themself (no OTP needed) or a caller whose **database** role is `ADMIN`. IT_ADMIN is not included. It is a soft delete; `DELETE /api/admin/users/{id}` is a hard delete.

**19. The `role` column only accepts the enum values it was created with.** PostgreSQL: `CHECK (role IN (...))`; H2: native `ENUM`. `ddl-auto: update` does not alter it. → [06 §3](06-data-model.md#3-how-the-role-column-is-defined-in-each-database)

## OTPs

**20. The attempt limit is enforced in some flows but not others.**
- Phone-OTP login, email-OTP login and phone signup run with `@Transactional(noRollbackFor = VerificationException.class)`. Wrong guesses are saved, and the 4th attempt gets `MAX_ATTEMPTS_EXCEEDED`.
- The other verify methods use a plain `@Transactional`: `PhoneVerificationService.verifyOtp` / `verifyChangeOtp`, `PasswordResetService.verifyOtpAndResetPassword` / `verifyEmailOtpAndResetPassword`, `UserService.deleteAccountWithOtp` and `AppleEmailVerificationService.verifyOtp`. The thrown `VerificationException` rolls back `attempts++`.
- Reproduced locally: on `/api/users/phone/change/verify`, five wrong codes each answered "Invalid OTP. 2 attempt(s) remaining.", `attempts` stayed 0, and the correct code was still accepted.

**21. OTPs are stored in plain text** (short-lived by design). Locally you can read them from the DB as well as the console. Link tokens (reset, email verification) are SHA-256 hashed and appear only in the console.

**22. Per-phone OTP limits survive restarts in the `local` profile**, because the DB is a file. If you hit `TOO_MANY_REQUESTS` while testing, wait 5 minutes or use another number.

## Environment

**23. `./run.sh` with no argument means `prod`** unless `.env` sets `SPRING_PROFILES_ACTIVE`. The local template sets it to `local`.

**24. Starting without the `local` profile fails** with "Client id of registration 'google' must not be empty" unless `GOOGLE_CLIENT_ID` is set.

**25. Rate-limit buckets are per instance and in memory.** They are cleared every 30 minutes and on restart. GETs are not limited (except paths containing `/verify`).

Next: [11-working-on-this-repo.md](11-working-on-this-repo.md)
