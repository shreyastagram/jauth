# Complete Testing Guide: Phases 1-4

## 📋 Overview

This guide covers comprehensive testing for all 4 phases of Java Auth integration:

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Password Management | ✅ Implemented |
| 2 | Google OAuth | ✅ Implemented |
| 3 | Profile Sync | ✅ Implemented |
| 4 | Session Management | ✅ Implemented |

## 🏗️ App Architecture

The app has **two separate login flows**:
- **User Flow**: UserTypeScreen → UserAuthScreen → LoginScreen/RegisterScreen (with `userType="user"`)
- **Provider Flow**: UserTypeScreen → ProviderAuthScreen → LoginScreen/ProviderRegisterScreen (with `userType="provider"`)

**Google Sign-In Available On:**
- ✅ Login screens (User & Provider)
- ✅ Register screens (User & Provider) - NEW!

**Important Security Rule**: 
- One email = One role (USER or SERVICE_PROVIDER)
- Google Sign-In enforces role matching
- Cannot use same email for both User and Provider accounts

**Dual Database Architecture:**
- **PostgreSQL (Java Auth)**: Authentication, tokens, passwords, sessions
- **MongoDB (Node.js)**: User/Provider profiles, business data
- Registration syncs to BOTH databases automatically

---

## 🔧 Prerequisites

### 1. Start All Services

**Terminal 1 - Java Auth Backend (Port 8080):**
```bash
cd jarbac
./mvnw spring-boot:run
```

**Terminal 2 - Node.js Backend (Port 5001):**
```bash
cd fixhomi-backend
npm run dev
```

**Terminal 3 - React Native App:**
```bash
cd renfi
npx react-native run-android --deviceId YOUR_DEVICE_ID
# Or for iOS: npx react-native run-ios
```

### 2. Verify Services Running

```bash
# Java Auth health check
curl http://localhost:8080/actuator/health

# Node.js backend check
curl http://localhost:5001/
```

---

# 🔐 PHASE 1: Password Management

## 1.1 Test User Registration (Prerequisite)

### Via cURL:
```bash
curl -X POST http://localhost:5001/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "Test@123456",
    "fullName": "Test User",
    "phoneNumber": "+919876543210"
  }'
```

### Via App (Form Registration):
1. Open app → Select "I need services" → Register
2. Fill in details (name, email, password) and submit
3. ✅ Expected: Account created in PostgreSQL + MongoDB

### Via App (Google Sign-In):
1. Open app → Select "I need services" → Register
2. Tap **"Continue with Google"**
3. Select Google account
4. ✅ Expected: Account created in PostgreSQL + MongoDB with USER role

---

## 1.2 Test Login

### Via cURL:
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "password": "Test@123456"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "userId": 1,
  "email": "testuser@example.com",
  "fullName": "Test User",
  "role": "USER",
  "expiresIn": 86400
}
```

**Save the tokens:**
```bash
export ACCESS_TOKEN="your_access_token_here"
export REFRESH_TOKEN="your_refresh_token_here"
```

### Via App:
1. Open app → Login screen
2. Enter email and password
3. ✅ Expected: Login successful, redirected to home

---

## 1.3 Test Forgot Password

### Via cURL:
```bash
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }'
```

**Expected Response:**
```json
{
  "message": "Password reset email sent",
  "success": true
}
```

### Via App:
1. On Login screen → Tap **"Forgot Password?"**
2. Enter your email
3. ✅ Expected: "Reset email sent" message

### 📱 Getting the Reset Token (Development):

Since emails use deep links (`fixhomi://auth/reset-password?token=xxx`), in development:

**Option 1: Check Java Auth Console Logs**
```
========== STUB EMAIL SERVICE ==========
EMAIL TYPE: Password Reset (Mobile + Web)
TO: testuser@example.com (Test User)
TOKEN: abc123xyz789...
MOBILE DEEP LINK: fixhomi://auth/reset-password?token=abc123xyz789
WEB FALLBACK URL: https://fixhomi.com/auth/reset-password?token=abc123xyz789
=========================================
```

**Option 2: Use token directly in app**
- Navigate to Reset Password screen manually
- Enter the token from console logs

---

## 1.4 Test Validate Reset Token

```bash
# Replace RESET_TOKEN with actual token from console logs
curl -X GET "http://localhost:8080/api/auth/reset-password/validate?token=RESET_TOKEN"
```

**Expected Response:**
```json
{
  "valid": true,
  "email": "testuser@example.com"
}
```

---

## 1.5 Test Reset Password

```bash
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "RESET_TOKEN_FROM_CONSOLE",
    "newPassword": "NewPassword@123"
  }'
```

**Expected Response:**
```json
{
  "message": "Password reset successfully",
  "success": true
}
```

### Via App (Deep Link - Production):
1. Click reset link from email → Opens app directly to Reset Password screen
2. Enter new password
3. ✅ Expected: Password reset, redirected to login

### Via App (Manual - Development):
1. If deep link doesn't work, use ResetPasswordScreen directly
2. Pass the token from console logs
3. Enter new password
4. ✅ Expected: Password reset successfully

---

## 1.6 Test Change Password (Logged In)

```bash
curl -X POST http://localhost:8080/api/users/change-password \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "currentPassword": "Test@123456",
    "newPassword": "NewSecure@789"
  }'
```

**Expected Response:**
```json
{
  "message": "Password changed successfully",
  "success": true
}
```

### Via App:
1. Login as User or Provider
2. Go to **Settings** tab (bottom navigation)
3. Scroll to **Security** section
4. Tap **"Change Password"**
5. Enter current password and new password
6. ✅ Expected: Password changed successfully

---

## 📊 Phase 1 Checklist

| Test | Status |
|------|--------|
| User Registration (via User flow) | ⬜ |
| Provider Registration (via Provider flow) | ⬜ |
| User Login with email/password | ⬜ |
| Provider Login with email/password | ⬜ |
| Forgot Password sends email | ⬜ |
| Reset token shown in console (dev) | ⬜ |
| Reset password with token | ⬜ |
| Login with new password | ⬜ |
| Change password (Settings → Security) | ⬜ |

---

# 🔑 PHASE 2: Google OAuth

## ⚠️ Critical Security: Role Enforcement

**One Email = One Role Rule:**
- When a user signs in with Google, they are assigned a role based on which login screen they use:
  - **User Login Screen** → `USER` role
  - **Provider Login Screen** → `SERVICE_PROVIDER` role
- **Existing accounts**: If the email already exists with a different role, login is BLOCKED with error: "ROLE_CONFLICT"
- **New accounts**: Role is set at first Google Sign-In and cannot be changed

## 2.1 Prerequisites

Ensure Google OAuth is configured:
- Google Cloud Console project set up
- OAuth 2.0 Client IDs created (Web, iOS, Android)
- Client IDs configured in frontend (`googleAuthService.js`)

## 2.2 Test Google Sign-In (New User as USER)

### Via Login Screen:
1. Open app → Select **"I need services"** (User)
2. On User Login screen, tap **"Continue with Google"**
3. Select Google account (use email not previously registered)
4. ✅ Expected: Account created as `USER`, redirected to User Home

### Via Register Screen (NEW):
1. Open app → Select **"I need services"** (User)
2. Tap "Register" to go to RegisterScreen
3. Tap **"Continue with Google"**
4. Select Google account
5. ✅ Expected: Account created as `USER` in BOTH PostgreSQL AND MongoDB

## 2.3 Test Google Sign-In (New Provider)

### Via Login Screen:
1. Open app → Select **"I provide services"** (Provider)
2. On Provider Login screen, tap **"Continue with Google"**
3. Select Google account (use NEW email not previously registered)
4. ✅ Expected: Account created as `SERVICE_PROVIDER`, redirected to Provider Home

### Via Register Screen (NEW - Recommended):
1. Open app → Select **"I provide services"** (Provider)
2. Tap "Register" to go to ProviderRegisterScreen
3. **Fill in Name and Address first** (recommended for complete profile)
4. Capture location if prompted
5. Tap **"Continue with Google"**
6. Select Google account
7. ✅ Expected: Account created as `SERVICE_PROVIDER` with prefilled profile data

💡 **Pro Tip**: For providers, filling in the form before Google Sign-In ensures their address and location are captured during registration.

## 2.4 Test Role Conflict (Critical Security Test)

### Scenario: User tries to login as Provider with existing User email

1. First, login via **User flow** with Google (creates USER account)
2. Logout
3. Select **"I provide services"** (Provider flow)
4. Try to login with **same Google account**
5. ✅ Expected: **Error message**: "This email is already registered as a User. Please login from the correct app screen."

### Scenario: Provider tries to login as User with existing Provider email

1. First, login via **Provider flow** with Google (creates SERVICE_PROVIDER account)
2. Logout
3. Select **"I need services"** (User flow)
4. Try to login with **same Google account**
5. ✅ Expected: **Error message**: "This email is already registered as a Service Provider. Please login from the correct app screen."

## 2.5 Test Google Sign-In (Existing User - Correct Flow)

### Via App:
1. Use an email that was previously registered via User flow
2. Select **"I need services"** → User Login
3. Tap **"Continue with Google"**
4. ✅ Expected: Logged in successfully, sees User Home

## 2.6 Test Google OAuth API Directly

```bash
# This requires a valid Google ID token from mobile
# For new USER:
curl -X POST http://localhost:8080/api/auth/oauth2/google/mobile \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "GOOGLE_ID_TOKEN_FROM_MOBILE",
    "role": "USER"
  }'

# For new SERVICE_PROVIDER:
curl -X POST http://localhost:8080/api/auth/oauth2/google/mobile \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "GOOGLE_ID_TOKEN_FROM_MOBILE",
    "role": "SERVICE_PROVIDER"
  }'
```

**Expected Response (Success):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "uuid...",
  "userId": 2,
  "email": "googleuser@gmail.com",
  "fullName": "Google User",
  "role": "USER",
  "isNewUser": true,
  "expiresIn": 86400
}
```

**Expected Response (Role Conflict):**
```json
{
  "error": "Authentication Failed",
  "message": "ROLE_CONFLICT: This email is already registered as a Service Provider. Please login from the correct app screen."
}
```

---

## 2.7 Test MongoDB Sync Endpoints (Node.js Backend)

When using Google OAuth from Register screens, the app automatically syncs the new user to MongoDB.
You can also call these endpoints directly for testing:

### Sync Google User to MongoDB:
```bash
curl -X POST http://localhost:5001/api/auth/google/sync-user \
  -H "Content-Type: application/json" \
  -d '{
    "javaUserId": "USER_ID_FROM_JAVA_AUTH",
    "email": "googleuser@gmail.com",
    "fullName": "Google User",
    "googleId": "google123",
    "profilePicture": "https://..."
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "code": "USER_SYNC_SUCCESS",
  "message": "Google user profile created successfully",
  "userId": "USER_ID",
  "userType": "user"
}
```

### Sync Google Provider to MongoDB:
```bash
curl -X POST http://localhost:5001/api/auth/google/sync-provider \
  -H "Content-Type: application/json" \
  -d '{
    "javaUserId": "PROVIDER_ID_FROM_JAVA_AUTH",
    "email": "provider@gmail.com",
    "name": "Provider Name",
    "address": "123 Main St, City",
    "googleId": "google456",
    "phone": "+919876543210",
    "city": "Mumbai",
    "latitude": 19.076,
    "longitude": 72.877
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "code": "PROVIDER_SYNC_SUCCESS",
  "message": "Google provider profile created successfully",
  "userId": "PROVIDER_ID",
  "userType": "provider"
}
```

### Check if Email Exists:
```bash
curl -X GET http://localhost:5001/api/auth/google/check-email/test@gmail.com
```

**Expected Response:**
```json
{
  "success": true,
  "exists": true,
  "userType": "user",
  "hasProfile": true
}
```

---

## 📊 Phase 2 Checklist

| Test | Status |
|------|--------|
| Google Sign-In from User Login screen (new account) | ⬜ |
| Google Sign-In from User Register screen (new) | ⬜ |
| Google Sign-In from Provider Login screen (new account) | ⬜ |
| Google Sign-In from Provider Register screen (new) | ⬜ |
| Existing User via correct flow (User) | ⬜ |
| Existing Provider via correct flow (Provider) | ⬜ |
| **SECURITY: User trying Provider flow = BLOCKED** | ⬜ |
| **SECURITY: Provider trying User flow = BLOCKED** | ⬜ |
| User data synced to MongoDB (via /api/auth/google/sync-user) | ⬜ |
| Provider data synced to MongoDB (via /api/auth/google/sync-provider) | ⬜ |
| isNewUser=true for first login | ⬜ |
| isNewUser=false for subsequent logins | ⬜ |

---

# 🔄 PHASE 3: Profile Sync

## 3.1 Test Get Current User (Java Auth)

```bash
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "id": 1,
  "email": "testuser@example.com",
  "fullName": "Test User",
  "phoneNumber": "+919876543210",
  "role": "USER",
  "isEmailVerified": false,
  "isPhoneVerified": false,
  "isActive": true,
  "createdAt": "2024-01-15T10:00:00Z"
}
```

---

## 3.2 Test Update Profile (Java Auth)

```bash
curl -X PUT http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Updated Test User",
    "phoneNumber": "+919876543211"
  }'
```

**Expected Response:**
```json
{
  "id": 1,
  "email": "testuser@example.com",
  "fullName": "Updated Test User",
  "phoneNumber": "+919876543211",
  "message": "Profile updated successfully"
}
```

---

## 3.3 Test Get User Profile (Node.js/MongoDB)

```bash
# Replace USER_MONGO_ID with MongoDB _id
curl -X GET http://localhost:5001/api/user/profile/USER_MONGO_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## 3.4 Test Profile Sync (Via App)

### Test 1: Update in Java Auth, verify in MongoDB
1. Open app → Profile
2. Update name
3. Save changes
4. ✅ Expected: Profile updated in both Java Auth and MongoDB

### Test 2: Verify bidirectional sync
1. Check MongoDB directly:
```bash
# In MongoDB shell or Compass
db.users.findOne({ email: "testuser@example.com" })
```
2. Verify name matches Java Auth

---

## 3.5 Test Phone Verification

```bash
# Send OTP
curl -X POST http://localhost:8080/api/auth/otp/send \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "+919876543210"
  }'

# Verify OTP (check console for OTP in dev mode)
curl -X POST http://localhost:8080/api/auth/otp/verify \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "+919876543210",
    "otp": "123456"
  }'
```

---

## 3.6 Test Email Verification

```bash
# Send verification email
curl -X POST http://localhost:8080/api/auth/email/send-verification \
  -H "Authorization: Bearer $ACCESS_TOKEN"

# Verify email (use token from email)
curl -X GET "http://localhost:8080/api/auth/email/verify?token=EMAIL_VERIFY_TOKEN"
```

---

## 📊 Phase 3 Checklist

| Test | Status |
|------|--------|
| Get current user from Java Auth | ⬜ |
| Update profile in Java Auth | ⬜ |
| Profile synced to MongoDB | ⬜ |
| Phone verification OTP sent | ⬜ |
| Phone OTP verified | ⬜ |
| Email verification sent | ⬜ |
| Email verified | ⬜ |

---

# 📱 PHASE 4: Session Management

## 4.1 Test Validate Token

```bash
curl -X GET http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "valid": true,
  "userId": 1,
  "email": "testuser@example.com",
  "role": "USER",
  "isEmailVerified": false,
  "isPhoneVerified": false
}
```

---

## 4.2 Test Get Active Sessions

```bash
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Device-Id: test_device_001"
```

**Expected Response:**
```json
{
  "sessions": [
    {
      "id": 1,
      "deviceId": "test_device_001",
      "deviceName": "Test Device",
      "deviceModel": "iPhone 15",
      "platform": "ios",
      "systemVersion": "17.0",
      "appVersion": "1.0.0",
      "ipAddress": "127.0.0.1",
      "isTrusted": false,
      "isCurrentSession": true,
      "lastActivityAt": "2024-01-15T10:30:00Z",
      "createdAt": "2024-01-15T10:00:00Z"
    }
  ],
  "count": 1
}
```

---

## 4.3 Test Multi-Device Login

### Step 1: Login from "Device 1"
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: device_001" \
  -d '{
    "email": "testuser@example.com",
    "password": "Test@123456"
  }'
```

### Step 2: Login from "Device 2"
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: device_002" \
  -d '{
    "email": "testuser@example.com",
    "password": "Test@123456"
  }'
```

### Step 3: Check sessions (should show 2)
```bash
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Device-Id: device_001"
```

---

## 4.4 Test Revoke Specific Session

```bash
# Get session ID from sessions list, then revoke
curl -X DELETE http://localhost:8080/api/auth/sessions/2 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Session revoked successfully"
}
```

---

## 4.5 Test Revoke All Other Sessions

```bash
curl -X POST http://localhost:8080/api/auth/sessions/revoke-all \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "exceptDeviceId": "device_001"
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Sessions revoked successfully",
  "revokedCount": 1
}
```

---

## 4.6 Test Trust Device

```bash
curl -X POST http://localhost:8080/api/auth/devices/trust \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "device_001",
    "deviceName": "My iPhone",
    "deviceModel": "iPhone 15 Pro",
    "platform": "ios",
    "systemVersion": "17.0",
    "appVersion": "1.0.0"
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Device trusted successfully",
  "deviceId": "device_001",
  "trustedAt": "2024-01-15T10:30:00Z"
}
```

---

## 4.7 Test Get Trusted Devices

```bash
curl -X GET http://localhost:8080/api/auth/devices/trust \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "devices": [
    {
      "id": 1,
      "deviceId": "device_001",
      "deviceName": "My iPhone",
      "customName": null,
      "deviceModel": "iPhone 15 Pro",
      "platform": "ios",
      "systemVersion": "17.0",
      "appVersion": "1.0.0",
      "trustedAt": "2024-01-15T10:30:00Z",
      "lastUsedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "count": 1
}
```

---

## 4.8 Test Untrust Device

```bash
curl -X DELETE http://localhost:8080/api/auth/devices/trust/device_001 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Device trust removed successfully"
}
```

---

## 4.9 Test Token Refresh

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "new_access_token...",
  "refreshToken": "new_refresh_token...",
  "expiresIn": 86400
}
```

---

## 4.10 Test Logout

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "YOUR_REFRESH_TOKEN"
  }'
```

**Expected Response:**
```json
{
  "message": "Logged out successfully"
}
```

---

## 📊 Phase 4 Checklist

| Test | Status |
|------|--------|
| Validate token returns user info | ⬜ |
| Get active sessions | ⬜ |
| Multi-device login creates sessions | ⬜ |
| Revoke specific session | ⬜ |
| Revoke all other sessions | ⬜ |
| Trust device | ⬜ |
| Get trusted devices | ⬜ |
| Untrust device | ⬜ |
| Token refresh works | ⬜ |
| Logout invalidates tokens | ⬜ |

---

# 📱 TESTING FROM REACT NATIVE APP

## App Navigation Structure

```
UserTypeScreen (First Screen)
├── "I need services" → UserAuthScreen
│   ├── LoginScreen (userType="user")
│   │   ├── "Forgot Password?" → ForgotPasswordScreen
│   │   └── "Continue with Google" → Google OAuth (USER role)
│   └── RegisterScreen
│       ├── Form Registration → Node.js API → Java Auth + MongoDB
│       └── "Continue with Google" → Google OAuth (USER role) + MongoDB sync
│
└── "I provide services" → ProviderAuthScreen
    ├── LoginScreen (userType="provider")
    │   ├── "Forgot Password?" → ForgotPasswordScreen
    │   └── "Continue with Google" → Google OAuth (SERVICE_PROVIDER role)
    └── ProviderRegisterScreen
        ├── Form Registration → Node.js API → Java Auth + MongoDB
        └── "Continue with Google" → Google OAuth (SERVICE_PROVIDER role) + MongoDB sync
```

## Phase 1 - Password Management Testing:

### Forgot Password:
1. Select **"I need services"** or **"I provide services"**
2. On Login screen → Tap **"Forgot Password?"**
3. Enter email → Submit
4. ✅ Check Java console for token
5. Use token to reset password

### Change Password (Logged In):
1. Login successfully
2. Go to **Settings** tab (bottom navigation)
3. Scroll to **Security** section
4. Tap **"Change Password"**
5. Enter current password and new password
6. ✅ Password changed

## Phase 2 - Google OAuth Testing:

### New User (via User Login):
1. Select **"I need services"**
2. Tap **"Continue with Google"** on Login screen
3. Select Google account
4. ✅ Created as USER, see User Home

### New User (via User Register - NEW):
1. Select **"I need services"**
2. Tap **"Register"**
3. Tap **"Continue with Google"**
4. Select Google account
5. ✅ Created as USER in PostgreSQL AND MongoDB

### New Provider (via Provider Login):
1. Select **"I provide services"**
2. Tap **"Continue with Google"** on Login screen
3. Select Google account (different email)
4. ✅ Created as SERVICE_PROVIDER, see Provider Home

### New Provider (via Provider Register - NEW - Recommended):
1. Select **"I provide services"**
2. Tap **"Register"**
3. **Fill in Name and Address first**
4. Get your location
5. Tap **"Continue with Google"**
6. Select Google account
7. ✅ Created as SERVICE_PROVIDER with complete profile

### ⚠️ Role Conflict Test:
1. Login via User flow with Google (email: test@gmail.com)
2. Logout
3. Select **"I provide services"**
4. Try same Google account (test@gmail.com)
5. ✅ Error: "This email is already registered as a User"

## Phase 3 - Profile Sync Testing:
1. Login as User or Provider
2. Go to **Profile** tab (bottom navigation)
3. Tap **Edit** → Change name/address
4. Save changes
5. ✅ Verify in both PostgreSQL and MongoDB

## Phase 4 - Session Management Testing:
1. Login as User or Provider
2. Go to **Settings** tab (bottom navigation)
3. Scroll to **Security** section
4. Tap **"Account Security"**
5. View active sessions
6. Test "Sign out from all devices"
7. Test "Trust This Device"

---

# 🗄️ DATABASE VERIFICATION

## PostgreSQL (Java Auth)

```sql
-- Check users and their roles
SELECT id, email, full_name, role, is_email_verified, is_phone_verified 
FROM users 
WHERE email = 'testuser@example.com';

-- Verify role enforcement (should only have ONE entry per email)
SELECT email, role, COUNT(*) as count 
FROM users 
GROUP BY email, role;

-- Check sessions
SELECT us.id, u.email, us.device_id, us.device_name, us.is_active, us.last_activity_at
FROM user_sessions us
JOIN users u ON us.user_id = u.id
ORDER BY us.last_activity_at DESC;

-- Check trusted devices
SELECT td.id, u.email, td.device_id, td.device_name, td.is_active
FROM trusted_devices td
JOIN users u ON td.user_id = u.id;

-- Check refresh tokens
SELECT rt.id, u.email, rt.token, rt.is_revoked, rt.expires_at
FROM refresh_tokens rt
JOIN users u ON rt.user_id = u.id
ORDER BY rt.created_at DESC;
```

## MongoDB (Node.js Backend)

```javascript
// In MongoDB shell
use fixhomi

// Check users
db.users.find({ email: "testuser@example.com" }).pretty()

// Check providers
db.providers.find({ email: "provider@example.com" }).pretty()
```

---

# ⚠️ TROUBLESHOOTING

## Common Issues

### 1. "401 Unauthorized"
**Cause:** Token expired or invalid
**Fix:** 
- Get new token via login
- Or refresh token: `POST /api/auth/refresh`

### 2. "No refresh token available" (Google Sign-In)
**Cause:** apiClient was trying to refresh tokens during OAuth flow
**Fix:** This is FIXED in the latest code. The `/oauth2` and `/google` endpoints now skip token refresh.
**Verification:** Check `apiClient.js` line ~127:
```javascript
const skipRefreshUrls = ['/refresh', '/login', '/register', '/forgot-password', '/reset-password', '/oauth2', '/google'];
```

### 3. "Connection refused on port 8080"
**Cause:** Java Auth not running
**Fix:** 
```bash
cd jarbac && ./run.sh
```

### 4. "Connection refused on port 5001"
**Cause:** Node.js backend not running
**Fix:** 
```bash
cd fixhomi-backend && npm run dev
```

### 4. "CORS Error" (Browser/Postman)
**Cause:** Cross-origin request blocked
**Fix:** Ensure backend allows CORS

### 5. "Google Sign-In Failed"
**Cause:** Invalid OAuth configuration
**Fix:** 
- Verify Client IDs in Google Console
- Verify Client IDs in frontend config
- Check SHA-1 fingerprint for Android

### 6. "Session not found"
**Cause:** Session already revoked or wrong user
**Fix:** Refresh sessions list

### 7. "Profile sync failed"
**Cause:** Network issue or token expired
**Fix:** 
- Check network connectivity
- Re-login to get fresh tokens

---

# 🎯 QUICK TEST SCRIPT

Save this as `test_all_phases.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
NODE_URL="http://localhost:5001"
EMAIL="testuser$(date +%s)@example.com"
PASSWORD="Test@123456"

echo "🔐 Testing FixHomi Auth - All Phases"
echo "======================================"

# Phase 1: Registration
echo -e "\n📝 Phase 1: Registration..."
REGISTER_RESPONSE=$(curl -s -X POST "$NODE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"fullName\":\"Test User\"}")
echo "Register: $REGISTER_RESPONSE"

# Phase 1: Login
echo -e "\n🔑 Phase 1: Login..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
echo "Login: $LOGIN_RESPONSE"

ACCESS_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.accessToken')
REFRESH_TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.refreshToken')

if [ "$ACCESS_TOKEN" != "null" ]; then
  echo "✅ Got access token"
  
  # Phase 3: Get profile
  echo -e "\n👤 Phase 3: Get Profile..."
  PROFILE=$(curl -s -X GET "$BASE_URL/api/users/me" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
  echo "Profile: $PROFILE"
  
  # Phase 4: Validate token
  echo -e "\n✅ Phase 4: Validate Token..."
  VALIDATE=$(curl -s -X GET "$BASE_URL/api/auth/validate" \
    -H "Authorization: Bearer $ACCESS_TOKEN")
  echo "Validate: $VALIDATE"
  
  # Phase 4: Get sessions
  echo -e "\n📱 Phase 4: Get Sessions..."
  SESSIONS=$(curl -s -X GET "$BASE_URL/api/auth/sessions" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "X-Device-Id: test_script_device")
  echo "Sessions: $SESSIONS"
  
  # Phase 4: Trust device
  echo -e "\n🔒 Phase 4: Trust Device..."
  TRUST=$(curl -s -X POST "$BASE_URL/api/auth/devices/trust" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"deviceId":"test_script_device","deviceName":"Test Script","platform":"macos"}')
  echo "Trust: $TRUST"
  
  echo -e "\n✅ All basic tests completed!"
else
  echo "❌ Login failed, cannot proceed with authenticated tests"
fi
```

Make executable and run:
```bash
chmod +x test_all_phases.sh
./test_all_phases.sh
```

---

# 📋 FINAL CHECKLIST

## All Phases Complete

| Phase | Feature | API Tests | App Tests |
|-------|---------|-----------|-----------|
| 1 | User Registration (Form) | ⬜ | ⬜ |
| 1 | Provider Registration (Form) | ⬜ | ⬜ |
| 1 | Login | ⬜ | ⬜ |
| 1 | Forgot Password | ⬜ | ⬜ |
| 1 | Reset Password | ⬜ | ⬜ |
| 1 | Change Password | ⬜ | ⬜ |
| 2 | Google Sign-In (User Login) | ⬜ | ⬜ |
| 2 | Google Sign-In (User Register) | ⬜ | ⬜ |
| 2 | Google Sign-In (Provider Login) | ⬜ | ⬜ |
| 2 | Google Sign-In (Provider Register) | ⬜ | ⬜ |
| 2 | Role Conflict (User→Provider) | ⬜ | ⬜ |
| 2 | Role Conflict (Provider→User) | ⬜ | ⬜ |
| 2 | MongoDB Sync (User) | ⬜ | ⬜ |
| 2 | MongoDB Sync (Provider) | ⬜ | ⬜ |
| 3 | Get Profile | ⬜ | ⬜ |
| 3 | Update Profile | ⬜ | ⬜ |
| 3 | Profile Sync | ⬜ | ⬜ |
| 3 | Phone Verification | ⬜ | ⬜ |
| 3 | Email Verification | ⬜ | ⬜ |
| 4 | Validate Token | ⬜ | ⬜ |
| 4 | Get Sessions | ⬜ | ⬜ |
| 4 | Revoke Session | ⬜ | ⬜ |
| 4 | Revoke All Sessions | ⬜ | ⬜ |
| 4 | Trust Device | ⬜ | ⬜ |
| 4 | Untrust Device | ⬜ | ⬜ |
| 4 | Token Refresh | ⬜ | ⬜ |
| 4 | Logout | ⬜ | ⬜ |

---

## API Endpoints Summary

### Java Auth (Port 8080)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Email/password login |
| POST | `/api/auth/oauth2/google/mobile` | Google OAuth |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout |
| POST | `/api/auth/forgot-password` | Send reset email |
| POST | `/api/auth/reset-password` | Reset password |
| POST | `/api/users/change-password` | Change password (auth required) |
| GET | `/api/users/me` | Get current user |
| PUT | `/api/users/profile` | Update profile |
| GET | `/api/auth/sessions` | Get active sessions |
| DELETE | `/api/auth/sessions/:id` | Revoke session |
| POST | `/api/auth/devices/trust` | Trust device |
| GET | `/api/auth/devices/trust` | Get trusted devices |

### Node.js Backend (Port 5001)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register user (→ Java Auth + MongoDB) |
| POST | `/api/auth/provider/register` | Register provider (→ Java Auth + MongoDB) |
| POST | `/api/auth/google/sync-user` | Sync Google user to MongoDB |
| POST | `/api/auth/google/sync-provider` | Sync Google provider to MongoDB |
| GET | `/api/auth/google/check-email/:email` | Check if email exists |
| GET | `/api/user/profile/:userId` | Get user profile from MongoDB |
| GET | `/api/auth/provider/profile/:providerId` | Get provider profile from MongoDB |

---

*Document Version: 2.0.0*
*Last Updated: January 2026 - Added Google Sign-In on Register screens, MongoDB sync endpoints*
