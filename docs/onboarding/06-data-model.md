# 06 · Data Model

14 tables, one per JPA entity in `entity/`. There are no migration files: Hibernate creates and extends the schema at startup (`spring.jpa.hibernate.ddl-auto: update` in every profile).

The column lists below come from the schema Hibernate actually generated: read from the local H2 database's `INFORMATION_SCHEMA`, and from the PostgreSQL DDL Hibernate 6.6 emits for these entities.

---

## 1. Relationships

```
                         ┌──────────────────────┐
                         │        users         │
                         │ id (PK, identity)    │
                         └──────────┬───────────┘
          real foreign keys         │
     ┌───────────────┬──────────────┼───────────────────┐
     ▼               ▼              ▼                   │
refresh_tokens   trusted_devices  user_sessions ──► refresh_tokens (refresh_token_id, nullable)
 (user_id FK)     (user_id FK)     (user_id FK)
 replaced_by_token ··► refresh_tokens.token   (logical rotation chain, not a FK)

     logical links only (a plain BIGINT user_id, no FK, no JPA association):
     phone_otps · email_otps · password_reset_otps · password_reset_tokens
     email_verification_tokens · delete_account_otps · login_lockouts (user_id nullable)

     keyed by something other than a user id:
     phone_signup_otps  (phone_number — the user doesn't exist yet)
     apple_email_otps   (apple_user_id)
```

Only `refresh_tokens`, `trusted_devices` and `user_sessions` have database foreign keys to `users`. This is why the admin hard delete removes those child rows explicitly before deleting the user.

---

## 2. `users`: the core table

Entity: `entity/User.java`.

| Column | Type (PostgreSQL) | Null | Constraint | Notes |
|---|---|---|---|---|
| `id` | bigint identity | no | PK | **The user id everywhere**: JWT `userId` claim, Node/Mongo `_id` |
| `email` | varchar(100) | **yes** | unique (`idx_email`) | Lowercased on save. `NULL` for phone-signup accounts. `deleted_<id>@del.local` after soft delete |
| `phone_number` | varchar(20) | yes | unique (`idx_phone`) | Normalised to 10 digits on save. `del_<id>` after soft delete |
| `password_hash` | varchar(60) | yes | | BCrypt. `NULL` for Google/Apple/phone-signup accounts (`hasPassword=false`) |
| `full_name` | varchar(100) | no | | may be `""` for phone signups without a name |
| `role` | varchar(20) | no | `check (role in ('USER','SERVICE_PROVIDER','ADMIN','SUPPORT','IT_ADMIN'))` | `@Enumerated(EnumType.STRING)`; see §3 |
| `is_active` | boolean | no | | `false` = disabled or soft-deleted. Checked on **every** authenticated request |
| `is_email_verified` | boolean | no | | |
| `is_phone_verified` | boolean | no | | |
| `apple_user_id` | varchar(255) | yes | unique | Apple `sub`, set on first Apple sign-in |
| `created_at` / `updated_at` | timestamp | no | | Spring Data auditing (`@CreatedDate` / `@LastModifiedDate`, enabled by `JpaConfig`) |
| `last_login_at` | timestamp | yes | | Set by every successful login |

`@PrePersist` / `@PreUpdate` → `normalizeFields()` (User.java:80–89):
- phone → `User.normalizePhoneNumber` (`+919356011874` / `919356011874` / `9356011874` → `9356011874`; values starting with `del_` are left as they are);
- email → trimmed and lowercased.

## 3. How the `role` column is defined in each database

The `Role` enum is mapped with `EnumType.STRING`, and Hibernate 6.6 turns it into a column that **only accepts the enum values that existed when the table was created**:

| Database | Generated definition |
|---|---|
| PostgreSQL (prod dialect) | `role varchar(20) not null check (role in ('USER','SERVICE_PROVIDER','ADMIN','SUPPORT','IT_ADMIN'))` |
| H2 (local/test) | native `ENUM('ADMIN','IT_ADMIN','SERVICE_PROVIDER','SUPPORT','USER')` |

`ddl-auto: update` only **adds** missing tables and columns. It never alters an existing column type or check constraint. The constraint on an existing database therefore reflects the enum at the time that table was first created. Inspect it with:

```sql
-- PostgreSQL
SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'c';
-- H2 (IntelliJ DB tool on ./.localdb)
SELECT VALUE_NAME FROM INFORMATION_SCHEMA.ENUM_VALUES WHERE OBJECT_NAME = 'USERS';
```

Locally, deleting `./.localdb` makes Hibernate recreate the table from the current enum.

## 4. Token and session tables

### `refresh_tokens` (`RefreshToken.java`)
| Column | Notes |
|---|---|
| `id` | PK |
| `token` | varchar(255), unique. The opaque 68-char value given to clients |
| `user_id` | FK → users |
| `expires_at` | created + 60 days |
| `revoked` | true after logout, rotation, password change/reset, disable or delete |
| `rotated_at` | set only when revoked **by rotation**. Enables the 45 s grace window |
| `replaced_by_token` | the successor token value |
| `created_at` | |

### `user_sessions` (`UserSession.java`)
- `user_id` FK; `refresh_token_id` FK (nullable, one-to-one).
- Device metadata: `device_id`, `device_name`, `device_model`, `platform`, `system_version`, `app_version`.
- Also: `ip_address`, `location`, `is_trusted`, `is_active`, `last_activity_at`, `created_at`, `updated_at`.

`SessionService.createOrUpdateSession` would populate this table, but **no flow calls it**, so it stays empty ([10](10-behaviours-and-gotchas.md)).

### `trusted_devices` (`TrustedDevice.java`)
- `user_id` FK, `device_id`; unique `(user_id, device_id)`.
- Also: `device_name`, `custom_name`, `device_model`, `platform`, `system_version`, `app_version`, `is_active`, `trusted_at`, `last_used_at`.
- Written by `POST /api/auth/devices/trust`.

### `login_lockouts` (`LoginLockout.java`)
- `identifier` (lowercased email or normalised phone), `user_id` (nullable), `failed_attempts`, `locked_until`, `last_attempt_at`, `created_at`.
- 5 failures → `locked_until = now + 15 min`.

## 5. One-time code and link tables

All have `id`, `expires_at`, `created_at`, and either `attempts` + `verified`/`used`. Codes are stored as **plain text** and are short-lived. **Link tokens are stored as SHA-256** (64 hex chars), so the raw value only exists in the email or console.

| Table | Entity | Keyed by | Code / token | Used by |
|---|---|---|---|---|
| `phone_otps` | `PhoneOtp` | `user_id`, `phone_number` | `otp` varchar(10) | phone OTP login, phone verify, phone change |
| `email_otps` | `EmailOtp` | `user_id`, `email` | `otp` varchar(10) | email OTP login |
| `phone_signup_otps` | `PhoneSignupOtp` | `phone_number` (+ `full_name`) | `otp` varchar(10) | phone signup (before the user exists) |
| `password_reset_otps` | `PasswordResetOtp` | `user_id` + `phone_number` **or** `email` | `otp` varchar(10) | forgot-password by phone / email |
| `password_reset_tokens` | `PasswordResetToken` | `user_id`, `email` | `token` varchar(64) SHA-256, unique | forgot-password link |
| `email_verification_tokens` | `EmailVerificationToken` | `user_id`, `email` | `token` varchar(64) SHA-256, unique | email verification link |
| `delete_account_otps` | `DeleteAccountOtp` | `user_id`, `phone_number` | `otp` varchar(10) | account deletion |
| `apple_email_otps` | `AppleEmailOtp` | `apple_user_id`, `email` | `otp` varchar(6) | Apple email fallback |

## 6. Repositories

One Spring Data interface per entity in `repository/`. Most queries are derived method names (`findByEmail`, `existsByPhoneNumberAndIsPhoneVerifiedTrueAndIsActiveTrue`, `findByRoleIn(List<Role>, Pageable)`); the time-based ones use explicit JPQL `@Query`. Example from `PhoneOtpRepository`:

```java
@Query("SELECT p FROM PhoneOtp p WHERE p.phoneNumber = :phoneNumber " +
       "AND p.verified = false AND p.expiresAt > :now ORDER BY p.createdAt DESC LIMIT 1")
Optional<PhoneOtp> findLatestValidOtpByPhone(@Param("phoneNumber") String phoneNumber,
                                             @Param("now") LocalDateTime now);
```

Bulk updates and deletes are `@Modifying @Query` (e.g. `invalidateAllUserOtps`, `revokeAllUserTokens`, cleanup jobs).

## 7. Looking at the data locally

- **IntelliJ / DBeaver:** JDBC URL `jdbc:h2:file:<repo>/.localdb/fixhomi_auth;AUTO_SERVER=TRUE`, user `sa`, empty password. This works while the app is running.
- **Command line:**
  ```bash
  java -cp ~/.m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar org.h2.tools.Shell \
    -url "jdbc:h2:file:$(pwd)/.localdb/fixhomi_auth;AUTO_SERVER=TRUE" -user sa -password "" \
    -sql "SELECT id, email, phone_number, role, is_active FROM users"
  ```
- **Reset:** stop the app, `rm -rf .localdb`, start again. The admin is re-seeded.

Production data lives in Neon PostgreSQL. Access is restricted; never point a local instance at it.

Next: [07-how-other-services-use-auth.md](07-how-other-services-use-auth.md)
