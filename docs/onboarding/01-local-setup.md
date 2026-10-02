# 01 · Local Setup

> Goal: in about 15 minutes you have the auth service running on `http://localhost:8080`, an ADMIN account to log in with, and the Postman collection talking to it. No production credentials are needed.

Every step below was run on a clean copy of the repository (no `.env`, no `target/`) and confirmed working.

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | **17** (the project targets `<java.version>17</java.version>` in `pom.xml`) | `java -version` |
| Git | any | `git --version` |
| Postman | desktop app | — |
| IDE | IntelliJ IDEA (recommended) or VS Code with Java extensions | — |
| OpenSSL | preinstalled on macOS/Linux | `openssl version` |

You do **not** need to install Maven: the repo ships the Maven Wrapper (`./mvnw`), which downloads Maven 3.9.11 on first run.
You do **not** need PostgreSQL, Docker, Google, Apple, Brevo or MSG91 accounts for local work.

---

## 2. Clone and build

```bash
git clone https://github.com/shreyastagram/jauth.git
cd jauth            # the folder is called jarbac inside the FIXORA monorepo checkout
./mvnw -DskipTests package
```

The first build downloads dependencies (a few minutes). A successful build ends with `BUILD SUCCESS`.

Run the test suite once to confirm your JDK is fine:

```bash
./mvnw test
```

Expected: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`. The test uses the `test` profile in `src/test/resources/application-test.yaml`, so it needs no environment variables.

---

## 3. Create your local `.env`

```bash
cp .env.local.example .env
openssl rand -base64 64 | tr -d '\n'      # copy the output
```

Open `.env` and replace the value of `JWT_SECRET` with the generated string. Your `.env` now contains:

```dotenv
JWT_SECRET=<your 88-character random string>
SPRING_PROFILES_ACTIVE=local
```

Why each line exists:

- **`JWT_SECRET`** — the HMAC key that signs every access token (HS512). `application.yaml` has `jwt.secret: ${JWT_SECRET}` with **no default**, so the app refuses to start without it. HS512 needs a key of at least 64 bytes; the command above produces 88 characters.
- **`SPRING_PROFILES_ACTIVE=local`** — activates `src/main/resources/application-local.yaml` (explained in §5).

`.env` is listed in `.gitignore`. Never commit it.

> Do **not** copy `.env.example` for local work. That file is the template for the *production* (Render) environment and expects a Neon PostgreSQL database.

---

## 4. Start the service

```bash
./run.sh local
```

`run.sh` loads `.env`, sets the profile, and calls `./mvnw spring-boot:run`. You should see:

```
🚀 Starting FixHomi Auth Service
   Profile: local
   Port: 8080
   Database: H2 (file: ./.localdb)
...
The following 1 profile is active: "local"
Started AuthServiceApplication in ~5 seconds
[local] Seeded ADMIN account: admin@fixhomi.local (password from fixhomi.local.seed-admin.password)
```

On later starts the last line becomes `[local] Seed admin already exists: admin@fixhomi.local`.

Verify from another terminal:

```bash
curl http://localhost:8080/api/auth/health
# {"status":"UP","message":"Auth service is running"}

curl http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

Stop the service with `Ctrl+C`.

---

## 5. What the `local` profile gives you

Spring Boot always loads `application.yaml`, then layers `application-<profile>.yaml` on top. With `local` active, `application-local.yaml` changes only these things:

| Setting | Base `application.yaml` | With `local` | Why |
|---|---|---|---|
| Database | H2 in-memory (`jdbc:h2:mem:fixhomi_auth`) | H2 **file** at `./.localdb/fixhomi_auth` with `AUTO_SERVER=TRUE` | Your test users survive restarts; you can open the DB from IntelliJ while the app runs |
| SMS / Email | `stub` unless env says otherwise | forced to `stub` | OTPs and links are **printed to the console** instead of being sent |
| Google web OAuth client id | `${GOOGLE_CLIENT_ID:}` (empty → startup failure) | `local-google-oauth-not-configured` unless you export a real one | Spring refuses to start with an empty client id; the browser Google flow is not used locally |
| Email / reset link base URLs | `https://auth.fixhomi.com` | `http://localhost:8080` | Links printed in the console point at your machine |
| Admin seeding | — | creates `admin@fixhomi.local` / `LocalAdmin@123` (role `ADMIN`) if missing | Public `/register` cannot create ADMIN/IT_ADMIN/SUPPORT; you need one admin to test `/api/admin/**` |
| Logging | Spring Security DEBUG, Hibernate SQL + bind TRACE | Spring Security INFO, Hibernate INFO, `com.fixhomi.auth` DEBUG | Readable console. Set `org.springframework.security` back to `DEBUG` when you want to trace an authorization decision |

The admin seeder is `src/main/java/com/fixhomi/auth/config/LocalDevDataSeeder.java`. It is annotated `@Profile("local")`, so it never runs in production (Render runs with `SPRING_PROFILES_ACTIVE=prod`).

To use different admin credentials, add to `.env`:

```dotenv
LOCAL_ADMIN_EMAIL=you@fixhomi.local
LOCAL_ADMIN_PASSWORD=Something@123
```

They are applied only when that email does not exist yet. To start from an empty database, stop the app and delete the folder:

```bash
rm -rf .localdb
```

---

## 6. Reading OTPs and links from the console

With the stub providers, nothing is sent to a phone or inbox. The service prints what *would* have been sent.

**SMS OTP** (`StubSmsService`):

```
========================================
     STUB SMS SERVICE - DEV MODE
========================================
 TEMPLATE: DEFAULT_LOGIN
 TO: 9876543210
 OTP CODE: 756631
 Use code 756631 to verify
========================================
```

**Email** (`StubEmailService`), e.g. password reset:

```
========== STUB EMAIL SERVICE ==========
EMAIL TYPE: Password Reset
TO: tej.user@example.com (Tej User)
TOKEN: <raw token>
...
```

Tip: keep a second terminal running a filter so you don't scroll:

```bash
./run.sh local 2>&1 | tee /tmp/jauth.log
# in another terminal
grep --line-buffered -E "OTP CODE|TOKEN:|URL" /tmp/jauth.log
```

---

## 7. Postman

Import both files from the `postman/` folder:

1. `postman/FixHomi_Auth_Service.postman_collection.json`
2. `postman/FixHomi_Auth_Local.postman_environment.json`

Select the environment **“FixHomi Auth – Local”** (top-right of Postman). Then follow the order of the folders: each request's *Tests* tab saves tokens and ids into environment variables, so later requests work without copy-pasting. Requests that need an OTP or an emailed token read it from the `OTP` / `RESET_TOKEN` / `EMAIL_VERIFY_TOKEN` variables: copy the value from the console (§6) into the environment before sending.

The collection has its own guide in the collection description (click the collection name in Postman).

**Rate limits while testing.** The service rate-limits per IP: 10 requests/min on login-type paths and 5/min on OTP-sending paths (see [04-security-jwt-rbac.md](04-security-jwt-rbac.md#6-rate-limiting)). If you use the Postman *Collection Runner* or click quickly you will get `429 Too Many Requests`. For bulk runs add `RATE_LIMIT_ENABLED=false` to `.env` and restart. Leave it enabled for normal manual testing so you see the real behaviour.

---

## 8. Running from IntelliJ IDEA

1. *File → Open* → select the repo folder (IntelliJ detects `pom.xml`).
2. *Project Structure → SDK* → a JDK 17.
3. Open `AuthServiceApplication.java` → click the green ▶ next to `main` → it fails the first time (no `JWT_SECRET`) — that's expected.
4. *Run → Edit Configurations → AuthServiceApplication*:
   - **Active profiles:** `local`
   - **Environment variables:** `JWT_SECRET=<the same value as in your .env>`
   - **Working directory:** the repo root (so `./.localdb` resolves to the same folder `run.sh` uses)
5. Run or Debug. Breakpoints in controllers/services now work.

### Inspecting the local database

The `/h2-console` web page is **not** reachable (it is not in the security permit list and frames are denied). Use IntelliJ instead:

*Database tool window → + → Data Source → H2* →

- URL: `jdbc:h2:file:<absolute path to repo>/.localdb/fixhomi_auth;AUTO_SERVER=TRUE`
- User: `sa`, password: empty

`AUTO_SERVER=TRUE` lets IntelliJ and the running app use the file at the same time.

---

## 9. Pointing the mobile app or Node backend at your local service (optional)

You do not need this for your first tasks. When you do:

- **React Native app** (`renfi/renfi`): set `USE_PRODUCTION_JAVA_AUTH = false` in `src/config/environment.js`. iOS simulator uses `LOCAL_MACHINE_IP` from `src/config/api.js`; Android emulator uses `10.0.2.2`; an Android phone over USB needs `adb reverse tcp:8080 tcp:8080`.
- **Node backend** (`noefi/fixhomi-backend`): set `JAVA_AUTH_URL=http://localhost:8080` **and** set its `JWT_SECRET` to exactly the same value as yours. Node verifies Java's tokens locally with that secret — see [07-how-other-services-use-auth.md](07-how-other-services-use-auth.md).

---

## 10. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | `.env` missing or not loaded | `cp .env.local.example .env`, set `JWT_SECRET`, start with `./run.sh local` |
| `Client id of registration 'google' must not be empty` | Started without the `local` profile (e.g. `./run.sh dev` or plain `./mvnw spring-boot:run`) | Use `./run.sh local`, or set *Active profiles* = `local` in IntelliJ |
| `Started ... with profile prod` and a Postgres connection error | `./run.sh` with no argument defaults to `prod` when `.env` has no `SPRING_PROFILES_ACTIVE` | `./run.sh local` |
| `Port 8080 was already in use` | Another instance is running | `lsof -i :8080` and stop it |
| `WeakKeyException` on first login | `JWT_SECRET` shorter than 64 bytes | Regenerate with `openssl rand -base64 64` |
| `429 Too Many Requests` | Per-IP rate limit | Wait 1 minute or set `RATE_LIMIT_ENABLED=false` |
| Postman shows a Google sign-in HTML page | You called a protected endpoint without a valid token; the service answers **302 → `/oauth2/authorize/google`** and Postman followed it | Log in again (token expired after 24 h) — see [10-behaviours-and-gotchas.md](10-behaviours-and-gotchas.md) |
| `401 {"code":"ACCOUNT_DELETED"}` on *any* request, even login | Postman sent an old `Authorization` header for a user that no longer exists (e.g. after `rm -rf .localdb`) | Clear `ACCESS_TOKEN` in the environment |

Next: [02-architecture.md](02-architecture.md)
