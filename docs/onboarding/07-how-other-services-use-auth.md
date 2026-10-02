# 07 · How Other Services Use jauth

jauth is only useful because other systems trust what it issues. This page explains, from their code, how each consumer calls jauth and what it relies on. Paths are relative to the `FIXORA_APP/` monorepo folder.

| Consumer | Code | Talks to jauth how |
|---|---|---|
| React Native app (customers + providers) | `renfi/renfi/src` | Directly, through the `authClient` axios instance, and indirectly via Node |
| Node.js backend | `noefi/fixhomi-backend` | **Verifies JWTs locally** with the shared secret; server-to-server calls for registration, phone signup, profile sync, deletion, admin proxy |
| Admin panel (Vite) | `temp_admin` | Never calls jauth directly; everything goes through Node's `/api/admin-mgmt/*` proxy |
| Website (Next.js) | `flapage/codespaces-nextjs/my-project` | Server-side route handlers call jauth for OTP login and account deletion |

---

## 1. The contract everyone depends on

These are the things that, if changed in jauth, break other systems:

| Contract | Value today | Who depends on it |
|---|---|---|
| `JWT_SECRET` | identical string in jauth and Node (`.env` of each) | Node REST middleware, Node Socket.IO auth |
| Algorithm | HS512 | Node pins `{ algorithms: ['HS512'] }` |
| Claim `userId` | `users.id` (number) | Node: Mongo `_id = String(userId)`; the `req.user` lookup |
| Claim `role` | `USER` / `SERVICE_PROVIDER` / `ADMIN` / `IT_ADMIN` / `SUPPORT` | Node `authenticateUser` (USER), `authenticateProvider` (SERVICE_PROVIDER), `requireAdmin` (ADMIN/IT_ADMIN) |
| Claim `sub` | email | Node super-admin check (`SUPER_ADMIN_EMAIL`) |
| `LoginResponse` field names | `accessToken`, `refreshToken`, `expiresIn` (seconds), `userId`, `role`, … | RN `storage.js`, Node, flapage |
| Refresh semantics | rotation on every refresh; **HTTP 401 = definitively invalid** | RN `apiClient.js` clears tokens only on 401 from `/refresh` |
| `ACCOUNT_DELETED` code | 401 body `{"code":"ACCOUNT_DELETED"}` | RN forces an immediate logout without trying to refresh |
| OTP error `code`s | `INVALID_OTP`, `OTP_EXPIRED`, `MAX_ATTEMPTS_EXCEEDED`, `USER_NOT_FOUND`, `ALREADY_REGISTERED`, `ROLE_CONFLICT`, … | RN screens choose their messages from these |

`iss` and `tokenType` are emitted but no consumer checks them.

**If `JWT_SECRET` differs** between jauth and Node, every Node `jwt.verify` throws. Node routes return 401, the app refreshes successfully against jauth, gets another token Node can't verify, and the user sees repeated failures, while jauth-only calls keep working. The dev-staging pair (`jauth-1` + `noefix-1-dev`) has the same requirement; `renfi/renfi/src/config/environment.js:42–51` warns about it.

---

## 2. React Native app (`renfi/renfi/src`)

### 2.1 Which server
`config/environment.js`:
- `USE_PRODUCTION_JAVA_AUTH = true` → `https://auth.fixhomi.com`
- `USE_DEV_STAGING = true` → `https://jauth-1.onrender.com` (with the dev Node)
- Local: set `USE_PRODUCTION_JAVA_AUTH = false`; `config/api.js` then uses `http://<LOCAL_MACHINE_IP or 10.0.2.2 or localhost>:8080`

### 2.2 Two HTTP clients
`services/apiClient.js` exports:
- `apiClient` → Node backend (business APIs)
- `authClient` → jauth (identity APIs)

Both request interceptors:
1. Add `X-App-Version` / `X-App-Platform`.
2. **Proactively refresh** if the access token expires within 5 minutes (`isTokenExpired(5 * 60 * 1000)`, line 100). This calls `POST {JAVA_AUTH_URL}/api/auth/refresh` with a plain axios call, guarded by a single-flight mutex so parallel requests share one refresh.
3. Attach `Authorization: Bearer <accessToken>`.

Error handling (`handleResponseError`):
- `401` with `code: "ACCOUNT_DELETED"` → clear tokens, call `global.onAuthExpired()` (set in `context/AppContext.js`), back to the login screen.
- Any other `401` → refresh once, retry the original request.
- Refresh fails with **401** → logout. Refresh fails with 429/5xx/network error (e.g. a Render cold start) → **keep the session** and surface a temporary error.

### 2.3 Token storage
`utils/storage.js`: `{accessToken, refreshToken, expiryTime}` goes to the **Keychain** (`fixhomi_auth`), mirrored to AsyncStorage. `expiryTime = Date.now() + expiresIn * 1000`, so the app relies on `expiresIn` rather than decoding the JWT.

### 2.4 jauth endpoints the app calls directly

| Area | Endpoints | File |
|---|---|---|
| Password login | `/api/auth/login`, `/api/auth/login/phone` | `services/authService.js` |
| OTP login | `/api/auth/login/{phone,email}/{send-otp,verify}` | `services/authService.js` |
| Verification | `/api/auth/otp/{send,verify}`, `/api/auth/email/{send-verification,verify}` | `services/authService.js` |
| Phone change | `/api/users/phone/change/{send-otp,verify}` | `services/authService.js` |
| Password | `/api/auth/forgot-password[/phone\|/email][/verify]`, `/api/users/change-password` | `services/authService.js` |
| Tokens | `/api/auth/refresh`, `/api/auth/logout` | `services/apiClient.js`, `authService.js`, `backgroundLocationService.js` |
| Social | `/api/auth/oauth2/google/mobile`, `/api/auth/oauth2/apple/{mobile,send-email-otp,verify-email-otp}` | `googleAuthService.js`, `appleAuthService.js` |
| Profile | `GET /api/users/me`, `PUT /api/users/profile` | `profileService.js`, `profileSyncService.js` |
| Sessions/devices | `/api/auth/sessions*`, `/api/auth/devices/trust*`, `/api/auth/validate`, `/actuator/health` | `authInfraService.js` |
| Account deletion | `/api/users/delete-account/request-otp`, `DELETE /api/users/account` | `screens/SettingsScreen.jsx` |

Registration, phone signup and the unified phone flow go **through Node** (next section), because Node also has to create the MongoDB profile.

---

## 3. Node.js backend (`noefi/fixhomi-backend`)

### 3.1 Verifying tokens: local, no network call

```js
// middlewares/authMiddleware.js:145 (same pattern at 241, 350, 454 and in adminAuthMiddleware.js, socketServer.js)
const decoded = jwt.verify(token, process.env.JWT_SECRET, { algorithms: ['HS512'] });
```

Node **does not** call `/api/token/validate` on every request; no consumer calls that endpoint. After verifying, Node uses the claims:

| Middleware | Requires | Then |
|---|---|---|
| `authenticateUser` | `role === 'USER'` | find Mongo `User` with `_id = String(decoded.userId)`; if missing, **auto-sync** by calling jauth `GET /api/users/me` and creating it |
| `authenticateProvider` | `role` is `PROVIDER` or `SERVICE_PROVIDER` | same for `Provider` |
| `dualAuth` | any | tries user, then provider |
| `authenticateToken` | any | only sets `req.auth = {userId, email, role}` |
| `requireAdmin` (`adminAuthMiddleware.js:16–47`) | `role` ∈ `['ADMIN','IT_ADMIN']`, else **403** | sets `req.admin`, keeps `req.adminToken` to forward to jauth |
| `requireSuperAdmin` | `requireAdmin` + `sub === SUPER_ADMIN_EMAIL` | |
| `validateWithJavaAuth` | — | online check for sensitive routes: calls jauth `GET /api/users/me` with the user's token; 401 → `TOKEN_REVOKED`; jauth 503 → allowed through |

### 3.2 The ID system
The MongoDB `_id` of a User/Provider document is the **string form of the jauth `users.id`**, also stored as `javaUserId`. Every service request, address and booking keyed by a user ultimately points at a jauth id.

### 3.3 Calls Node makes to jauth
`utils/authServiceClient.js`, base `process.env.JAVA_AUTH_URL` (Node refuses to start without it):

| Node function | jauth endpoint | Token sent |
|---|---|---|
| `registerUserInJavaAuth`, `registerProviderInJavaAuth` | `POST /api/auth/register` | none |
| `sendPhoneSignupOtp`, `verifyPhoneSignupOtp` | `POST /api/auth/signup/phone/{send-otp,verify}` | none |
| `sendPhoneLoginOtp`, `verifyPhoneLoginOtp` | `POST /api/auth/login/phone/{send-otp,verify}` | none |
| `loginUserInJavaAuth` | `POST /api/auth/login` | none |
| `revokeJavaAuthRefreshToken` | `POST /api/auth/logout` | none |
| `getJavaAuthUser`, `validateToken` | `GET /api/users/me` | user's |
| `syncProfileToJavaAuth` | `PUT /api/users/profile` | user's |
| `setEmailInJavaAuth` | `POST /api/users/email` | user's |
| `requestDeleteAccountOtp`, `deleteAccountWithOtp` | `/api/users/delete-account/request-otp`, `DELETE /api/users/account` | user's |
| `adminGetJavaAuthUser`, `adminListJavaAuthUsers` | `GET /api/admin/users/{id}`, `/list` | admin's |

Admin proxy (`controllers/adminManagementController.js`, used by the admin panel):

| Node route (from the panel) | jauth endpoint |
|---|---|
| `POST /api/admin-mgmt/login` | `POST /api/auth/login`, then Node accepts roles `['ADMIN','IT_ADMIN','SUPPORT']` (line 78) |
| create admin | `POST /api/admin/users` (roles `ADMIN`/`IT_ADMIN`/`SUPPORT`, line 168) |
| list / detail / status / hard delete | `/api/admin/users/list`, `/{id}`, `PATCH /{id}/status`, `DELETE /{id}` |

**SUPPORT in Node today:** `adminLogin` lets a SUPPORT user sign in to the admin panel, but every protected admin route uses `requireAdmin`, which only accepts `ADMIN`/`IT_ADMIN`. Node returns 403 for SUPPORT there, just as jauth denies SUPPORT on `/api/admin/**`.

---

## 4. Admin panel (`temp_admin`)

- Login: `POST {node}/api/admin-mgmt/login` → Node → jauth `/api/auth/login`. The returned jauth JWT is stored as `admin_token` in `localStorage`.
- Every admin action is sent to Node with that token. Node verifies it locally (`requireAdmin`) and forwards it unchanged to jauth `/api/admin/users/**`, where jauth applies its own `hasAnyRole('ADMIN','IT_ADMIN')`.
- The panel shows a jauth URL in its Settings page but never calls it.

## 5. Website (`flapage/codespaces-nextjs/my-project`)

Server-side Next.js route handlers (`app/api/account/*`) call jauth directly using `JAVA_AUTH_URL` (default `https://auth.fixhomi.com`):
- `send-otp` → `/api/auth/login/{email|phone}/send-otp`
- `verify-otp` → `/api/auth/login/{email|phone}/verify`. Tokens are kept in httpOnly cookies `fx_at` / `fx_rt`.
- `refresh` → `/api/auth/refresh`
- `profile` → `GET /api/users/me`
- `delete/request-otp` → `/api/users/delete-account/request-otp`; `delete` → `DELETE /api/users/account`, then Node `/api/auth/cleanup-account`

## 6. End-to-end example: a customer opens their service history

```
App                      Node (noefi)                         jauth
 │ GET /api/user/service-history/:userId  (routes/userRoutes.js:42)
 │  Authorization: Bearer <JWT signed by jauth>                  │
 ├──────────────────────►│ jwt.verify(token, JWT_SECRET, HS512) │   ← no call to jauth
 │                       │ authenticateUser: role === 'USER' ✔   │
 │                       │ User.findById(String(decoded.userId)) │
 │                       │ requireParamOwnership('userId') ✔     │
 │                       │   (missing? → GET /api/users/me ─────►│ JwtAuthenticationFilter → UserController)
 │◄──────────────────────┤ that user's service history            │
```

jauth only gets involved when the token needs refreshing, the user changes identity data, or Node needs to sync or verify something online.

Next: [08-walkthrough-send-otp-and-validate-token.md](08-walkthrough-send-otp-and-validate-token.md)
