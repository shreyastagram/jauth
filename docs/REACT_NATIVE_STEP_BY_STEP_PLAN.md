# FixHomi Auth Service - React Native Integration Plan

> **Step-by-step integration guide for React Native mobile app (USER & SERVICE_PROVIDER only)**  
> **Last Updated:** December 2024

---

## 📱 Overview

This guide covers the **mobile app integration** for FixHomi Auth Service. The mobile app supports only:
- **USER** - Regular customers who book services
- **SERVICE_PROVIDER** - Plumbers, electricians, etc. who provide services

> ⚠️ **Note:** ADMIN, SUPPORT, and IT_ADMIN roles are for the web admin panel only.

---

## 🎯 API Endpoints for React Native

### Complete API List (Mobile App Only)

| # | API | Method | Purpose | Auth Required |
|---|-----|--------|---------|---------------|
| 1 | `/api/auth/register` | POST | Register new user/provider | ❌ No |
| 2 | `/api/auth/login` | POST | Login user/provider | ❌ No |
| 3 | `/api/auth/logout` | POST | Logout (revoke refresh token) | ✅ Yes |
| 4 | `/api/auth/refresh` | POST | Get new access token | ❌ No |
| 5 | `/api/auth/health` | GET | Check service status | ❌ No |
| 6 | `/api/auth/token/validate` | GET | Validate token | ✅ Yes |
| 7 | `/api/auth/token/me` | GET | Get user from token | ✅ Yes |
| 8 | `/api/users/me` | GET | Get full profile | ✅ Yes |
| 9 | `/api/users/me/change-password` | POST | Change password | ✅ Yes |
| 10 | `/api/verification/otp/send` | POST | Send phone OTP | ✅ Yes |
| 11 | `/api/verification/otp/verify` | POST | Verify phone OTP | ✅ Yes |
| 12 | `/api/verification/email/send-verification` | POST | Send email verification | ✅ Yes |
| 13 | `/api/verification/email/verify` | GET | Verify email (deep link) | ❌ No |
| 14 | `/api/verification/forgot-password` | POST | Request password reset | ❌ No |
| 15 | `/api/verification/reset-password/validate` | GET | Validate reset token | ❌ No |
| 16 | `/api/verification/reset-password` | POST | Reset password | ❌ No |
| 17 | `/oauth2/authorization/google` | GET | Google OAuth login | ❌ No |

---

## 🔄 How Authentication Works

### Token Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AUTHENTICATION FLOW                                │
└─────────────────────────────────────────────────────────────────────────────┘

    USER/PROVIDER                    REACT NATIVE APP                 AUTH SERVICE
         │                                 │                               │
         │  1. Enter email/password        │                               │
         │────────────────────────────────▶│                               │
         │                                 │  2. POST /api/auth/login      │
         │                                 │──────────────────────────────▶│
         │                                 │                               │
         │                                 │  3. Return tokens + user info │
         │                                 │◀──────────────────────────────│
         │                                 │                               │
         │                                 │  4. Store tokens securely     │
         │                                 │  (Keychain/Keystore)          │
         │  5. Navigate to Home            │                               │
         │◀────────────────────────────────│                               │
         │                                 │                               │
```

### Token Types

| Token | Location | Expiry | Purpose |
|-------|----------|--------|---------|
| **Access Token (JWT)** | Keychain/Keystore | 24 hours | Used in `Authorization: Bearer <token>` header for API calls |
| **Refresh Token (UUID)** | Keychain/Keystore | 7 days | Used to get new access token when expired |

### Auto Token Refresh Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         TOKEN REFRESH FLOW                                   │
└─────────────────────────────────────────────────────────────────────────────┘

    REACT NATIVE APP                         AUTH SERVICE
         │                                        │
         │  1. API call with expired token        │
         │───────────────────────────────────────▶│
         │                                        │
         │  2. Returns 401 Unauthorized           │
         │◀───────────────────────────────────────│
         │                                        │
         │  3. POST /api/auth/refresh             │
         │     { refreshToken: "..." }            │
         │───────────────────────────────────────▶│
         │                                        │
         │  4. Returns NEW tokens                 │
         │     (old refresh token revoked)        │
         │◀───────────────────────────────────────│
         │                                        │
         │  5. Store new tokens                   │
         │  6. Retry original request             │
         │───────────────────────────────────────▶│
         │                                        │
```

---

## 📋 Step-by-Step Integration Plan

### Phase 1: Project Setup (Day 1)

#### Step 1.1: Install Dependencies

```bash
npm install axios react-native-keychain @react-native-async-storage/async-storage
npm install @react-native-google-signin/google-signin  # For Google OAuth

cd ios && pod install && cd ..
```

#### Step 1.2: Create Folder Structure

```
src/
├── api/
│   ├── client.js           # Axios instance
│   └── authService.js      # Auth API calls
├── services/
│   └── tokenService.js     # Secure token storage
├── context/
│   └── AuthContext.js      # Auth state management
├── screens/
│   └── auth/
│       ├── LoginScreen.js
│       ├── RegisterScreen.js
│       ├── ForgotPasswordScreen.js
│       └── ResetPasswordScreen.js
└── utils/
    └── constants.js        # API URLs
```

#### Step 1.3: Configure API Base URL

```javascript
// src/utils/constants.js
import { Platform } from 'react-native';

export const API_BASE_URL = __DEV__
  ? Platform.select({
      ios: 'http://localhost:8080',
      android: 'http://10.0.2.2:8080',  // Android emulator
    })
  : 'https://auth.fixhomi.com';  // Production
```

---

### Phase 2: Core Authentication (Day 2-3)

#### Step 2.1: Token Storage Service

**File:** `src/services/tokenService.js`

This service securely stores tokens using iOS Keychain / Android Keystore.

| Method | Purpose |
|--------|---------|
| `storeTokens(accessToken, refreshToken)` | Save both tokens |
| `getAccessToken()` | Get access token |
| `getRefreshToken()` | Get refresh token |
| `clearTokens()` | Remove all tokens (logout) |
| `isLoggedIn()` | Check if tokens exist |

---

#### Step 2.2: API Client Setup

**File:** `src/api/client.js`

Creates an Axios instance that:
1. ✅ Automatically adds `Authorization: Bearer <token>` to protected requests
2. ✅ Automatically refreshes token on 401 errors
3. ✅ Handles network errors gracefully

---

#### Step 2.3: Integrate Health Check API

**API:** `GET /api/auth/health`

**Purpose:** Check if Auth Service is running (useful for debugging)

**When to use:** 
- App startup to verify backend connectivity
- Display "Service unavailable" message if down

```javascript
// Usage
const isHealthy = await authService.healthCheck();
if (!isHealthy) {
  Alert.alert('Service Unavailable', 'Please try again later');
}
```

---

#### Step 2.4: Integrate Register API

**API:** `POST /api/auth/register`

**Purpose:** Create new USER or SERVICE_PROVIDER account

**Request:**
```json
{
  "email": "user@example.com",
  "phoneNumber": "+1234567890",
  "password": "SecurePass123!",
  "fullName": "John Doe",
  "role": "USER"  // or "SERVICE_PROVIDER"
}
```

**Response (201):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Handling:**
| Status | Error | User Message |
|--------|-------|--------------|
| 400 | Validation | Show field errors (email format, password requirements) |
| 409 | Duplicate | "Email or phone number already registered" |

**Password Requirements:**
- Minimum 8 characters
- At least 1 uppercase letter (A-Z)
- At least 1 lowercase letter (a-z)
- At least 1 digit (0-9)
- At least 1 special character (!@#$%^&*)

---

#### Step 2.5: Integrate Login API

**API:** `POST /api/auth/login`

**Purpose:** Authenticate existing user

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response (200):** Same as register response

**Error Handling:**
| Status | Error | User Message |
|--------|-------|--------------|
| 401 | Invalid credentials | "Invalid email or password" |
| 401 | Account disabled | "Your account has been disabled. Contact support." |

**After Successful Login:**
1. Store tokens in Keychain
2. Store user data
3. Navigate to Home screen (USER) or Provider Dashboard (SERVICE_PROVIDER)

---

#### Step 2.6: Integrate Logout API

**API:** `POST /api/auth/logout`

**Purpose:** Revoke refresh token and clear local session

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200):**
```json
{
  "message": "Logged out successfully"
}
```

**Logout Flow:**
1. Call logout API (revokes server-side refresh token)
2. Clear local tokens (even if API fails)
3. Navigate to Login screen

---

#### Step 2.7: Integrate Refresh Token API

**API:** `POST /api/auth/refresh`

**Purpose:** Get new access token when current one expires

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200):** Same as login response (includes NEW refresh token)

> ⚠️ **Important:** Token rotation is enabled. Each refresh generates a NEW refresh token. The old one becomes invalid.

**Automatic Refresh Logic:**
```
1. Make API call
2. If 401 returned:
   a. Get refresh token from storage
   b. Call /api/auth/refresh
   c. Store new tokens
   d. Retry original request
3. If refresh also fails:
   a. Clear tokens
   b. Navigate to login
```

---

### Phase 3: User Profile (Day 4)

#### Step 3.1: Integrate Get Profile API

**API:** `GET /api/users/me`

**Purpose:** Get full user profile information

**Headers:** `Authorization: Bearer <access_token>`

**Response (200):**
```json
{
  "id": 1,
  "email": "user@example.com",
  "phoneNumber": "+1234567890",
  "fullName": "John Doe",
  "role": "USER",
  "active": true,
  "emailVerified": false,
  "phoneVerified": false,
  "createdAt": "2024-12-14T10:30:00",
  "updatedAt": "2024-12-14T12:00:00",
  "lastLoginAt": "2024-12-19T15:30:00"
}
```

**When to Use:**
- Profile screen
- Check verification status
- Display user info in settings

---

#### Step 3.2: Integrate Get User from Token API

**API:** `GET /api/auth/token/me`

**Purpose:** Quick user info from JWT token (lighter than full profile)

**Response (200):**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "phoneNumber": "+1234567890",
  "emailVerified": true,
  "phoneVerified": false,
  "active": true,
  "createdAt": "2024-12-14T10:30:00"
}
```

**When to Use:**
- App startup to restore session
- Quick user data without full profile fetch

---

#### Step 3.3: Integrate Change Password API

**API:** `POST /api/users/me/change-password`

**Purpose:** Change password for logged-in user

**Request:**
```json
{
  "currentPassword": "OldPassword123!",
  "newPassword": "NewSecurePass456!"
}
```

**Response (200):**
```json
{
  "message": "Password changed successfully"
}
```

**Error Handling:**
| Status | Error | User Message |
|--------|-------|--------------|
| 400 | Validation | "Password must meet requirements" |
| 401 | Wrong password | "Current password is incorrect" |

---

### Phase 4: Phone Verification (Day 5)

#### Step 4.1: Integrate Send OTP API

**API:** `POST /api/verification/otp/send`

**Purpose:** Send 6-digit OTP to phone number

**Request:**
```json
{
  "phoneNumber": "+1234567890"
}
```

**Response (200):**
```json
{
  "message": "OTP sent successfully",
  "expiresInSeconds": 300
}
```

**Error Handling:**
| Status | Error | User Message |
|--------|-------|--------------|
| 400 | Invalid phone | "Please enter a valid phone number" |
| 429 | Rate limit | "Please wait before requesting another OTP" |

**OTP Settings:**
- Expires in: **5 minutes**
- Max attempts: **3**
- Rate limit: **1 request per minute**

---

#### Step 4.2: Integrate Verify OTP API

**API:** `POST /api/verification/otp/verify`

**Purpose:** Verify phone number with OTP

**Request:**
```json
{
  "phoneNumber": "+1234567890",
  "otp": "123456"
}
```

**Response (200):**
```json
{
  "message": "Phone verified successfully",
  "phoneVerified": true
}
```

**Error Handling:**
| Status | Error | User Message |
|--------|-------|--------------|
| 400 | Invalid OTP | "Invalid OTP. Please try again." |
| 400 | Expired | "OTP has expired. Request a new one." |
| 429 | Max attempts | "Too many attempts. Request a new OTP." |

---

### Phase 5: Email Verification (Day 6)

#### Step 5.1: Integrate Send Email Verification API

**API:** `POST /api/verification/email/send-verification`

**Purpose:** Send verification link to user's email

**Request:** No body required

**Response (200):**
```json
{
  "message": "Verification email sent",
  "expiresInHours": 24
}
```

**Email Settings:**
- Expires in: **24 hours**
- Rate limit: **1 email per 5 minutes**

---

#### Step 5.2: Handle Email Verification Deep Link

**API:** `GET /api/verification/email/verify?token=<token>`

**Purpose:** Verify email when user clicks link in email

**Setup Deep Linking:**

```javascript
// App.js
import { Linking } from 'react-native';

// Deep link format: fixhomi://verify-email?token=abc123
const linking = {
  prefixes: ['fixhomi://'],
  config: {
    screens: {
      VerifyEmail: 'verify-email',
    },
  },
};
```

**Response (200):**
```json
{
  "message": "Email verified successfully",
  "emailVerified": true
}
```

---

### Phase 6: Password Reset (Day 7)

#### Step 6.1: Integrate Forgot Password API

**API:** `POST /api/verification/forgot-password`

**Purpose:** Request password reset email

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response (200):** Always returns success to prevent email enumeration
```json
{
  "message": "If an account exists with this email, a password reset link will be sent"
}
```

> 🔒 **Security:** Always show success message regardless of whether email exists.

---

#### Step 6.2: Handle Reset Password Deep Link

**API:** `GET /api/verification/reset-password/validate?token=<token>`

**Purpose:** Validate reset token before showing password form

**Response (200):**
```json
{
  "valid": true,
  "email": "j***@example.com"
}
```

**Response (400):**
```json
{
  "valid": false,
  "message": "Token expired or invalid"
}
```

---

#### Step 6.3: Integrate Reset Password API

**API:** `POST /api/verification/reset-password`

**Purpose:** Set new password using reset token

**Request:**
```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewSecurePass789!"
}
```

**Response (200):**
```json
{
  "message": "Password reset successfully"
}
```

**After Success:**
- Navigate to Login screen
- Show message: "Password reset successfully. Please login with your new password."

---

### Phase 7: Google OAuth (Day 8)

#### Step 7.1: Setup Google Sign-In

```bash
npm install @react-native-google-signin/google-signin
```

#### Step 7.2: Configure in Google Cloud Console

1. Create OAuth 2.0 credentials for iOS and Android
2. Add iOS Bundle ID and Android SHA-1 fingerprint
3. Get Web Client ID

#### Step 7.3: Integrate Google OAuth

**API:** `GET /oauth2/authorization/google`

**Flow:**
```
1. User taps "Sign in with Google"
2. Open Auth Service URL in browser
3. User authenticates with Google
4. Auth Service handles callback
5. Redirects to app with tokens: fixhomi://oauth-callback?token=<jwt>&refresh=<refresh_token>
6. App extracts and stores tokens
7. Navigate to Home
```

---

### Phase 8: Auth Context & Protected Routes (Day 9)

#### Step 8.1: Create Auth Context

```javascript
// src/context/AuthContext.js
const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // Methods: login, register, logout, refreshUser
  
  return (
    <AuthContext.Provider value={{ user, loading, isAuthenticated, ... }}>
      {children}
    </AuthContext.Provider>
  );
};
```

#### Step 8.2: App Navigation Structure

```javascript
// App.js
const App = () => {
  const { isAuthenticated, loading, user } = useAuth();

  if (loading) return <SplashScreen />;

  return (
    <NavigationContainer>
      {!isAuthenticated ? (
        <AuthStack />  // Login, Register, ForgotPassword
      ) : user.role === 'SERVICE_PROVIDER' ? (
        <ProviderStack />  // Provider Dashboard
      ) : (
        <UserStack />  // User Home
      )}
    </NavigationContainer>
  );
};
```

---

## 📊 Integration Checklist

### Phase 1: Setup ✅
- [ ] Install dependencies
- [ ] Create folder structure
- [ ] Configure API base URL

### Phase 2: Core Auth ✅
- [ ] Token storage service
- [ ] API client with interceptors
- [ ] Health check
- [ ] Register (USER)
- [ ] Register (SERVICE_PROVIDER)
- [ ] Login
- [ ] Logout
- [ ] Auto token refresh

### Phase 3: Profile ✅
- [ ] Get profile
- [ ] Get user from token
- [ ] Change password

### Phase 4: Phone Verification ✅
- [ ] Send OTP
- [ ] Verify OTP

### Phase 5: Email Verification ✅
- [ ] Send verification email
- [ ] Handle deep link verification

### Phase 6: Password Reset ✅
- [ ] Forgot password
- [ ] Validate reset token
- [ ] Reset password

### Phase 7: Google OAuth ✅
- [ ] Configure Google Sign-In
- [ ] Handle OAuth flow
- [ ] Handle callback deep link

### Phase 8: Context & Navigation ✅
- [ ] Auth context
- [ ] Protected routes
- [ ] Role-based navigation

---

## 🔐 Security Reminders

1. **Never log tokens** - Even in development
2. **Use Keychain/Keystore** - Not AsyncStorage for tokens
3. **HTTPS in production** - Never HTTP
4. **Clear tokens on logout** - Even if API fails
5. **Handle disabled accounts** - Check for 401 with "disabled" message

---

## 🚨 Common Errors & Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| `Network Error` | Service unreachable | Check API URL, internet connection |
| `401 Unauthorized` | Token expired | Auto-refresh should handle; if not, logout |
| `401 Account disabled` | Admin disabled account | Show message, logout |
| `409 Conflict` | Duplicate email/phone | Show "already registered" message |
| `429 Too Many Requests` | Rate limit exceeded | Show "wait and retry" message |

---

## 📅 Estimated Timeline

| Phase | Days | Features |
|-------|------|----------|
| Phase 1 | 1 | Project setup |
| Phase 2 | 2 | Core auth (register, login, logout, refresh) |
| Phase 3 | 1 | User profile |
| Phase 4 | 1 | Phone verification |
| Phase 5 | 1 | Email verification |
| Phase 6 | 1 | Password reset |
| Phase 7 | 1 | Google OAuth |
| Phase 8 | 1 | Context & navigation |
| **Total** | **9 days** | **Full integration** |

---

## 🎯 Quick Reference

### Headers for Protected APIs
```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json
```

### User Roles (Mobile Only)
- `USER` - Regular customers
- `SERVICE_PROVIDER` - Service providers

### Token Expiry
- Access Token: **24 hours**
- Refresh Token: **7 days**

### Verification Limits
- Phone OTP: **5 min expiry, 3 attempts**
- Email: **24 hours expiry**
- Password Reset: **1 hour expiry**

---

**Questions?** Contact FixHomi Engineering Team.
