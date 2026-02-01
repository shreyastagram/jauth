# Phase 4: Session Management API Testing Guide

This guide covers complete testing of the Phase 4 Auth Infrastructure endpoints for multi-device session management and device trust.

## 📋 Prerequisites

1. **Java Auth Backend running** on port 8080
2. **PostgreSQL database** (Neon) connected
3. **Valid JWT access token** from login

---

## 🔧 API Endpoints Created

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/sessions` | Get all active sessions |
| DELETE | `/api/auth/sessions/{sessionId}` | Revoke a specific session |
| POST | `/api/auth/sessions/revoke-all` | Revoke all sessions except current |
| GET | `/api/auth/validate` | Validate current access token |
| POST | `/api/auth/devices/trust` | Trust current device |
| GET | `/api/auth/devices/trust` | Get all trusted devices |
| DELETE | `/api/auth/devices/trust/{deviceId}` | Remove trust from device |

---

## 🧪 Testing with cURL

### 1. First, Login to Get Tokens

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "your-email@example.com",
    "password": "your-password"
  }'
```

**Expected Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "abc123...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "your-email@example.com",
    "role": "USER"
  }
}
```

Save the `accessToken` for subsequent requests.

---

### 2. Validate Token

```bash
curl -X GET http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "valid": true,
  "userId": 1,
  "email": "your-email@example.com",
  "role": "USER",
  "isEmailVerified": true,
  "isPhoneVerified": false
}
```

---

### 3. Get Active Sessions

```bash
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "X-Device-Id: your-device-id"
```

**Expected Response:**
```json
{
  "sessions": [
    {
      "id": 1,
      "deviceId": "ios_abc123_1234567890",
      "deviceName": "iPhone 15",
      "deviceModel": "iPhone 15 Pro",
      "platform": "ios",
      "systemVersion": "17.0",
      "appVersion": "1.0.0",
      "ipAddress": "192.168.1.1",
      "location": null,
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

### 4. Trust a Device

```bash
curl -X POST http://localhost:8080/api/auth/devices/trust \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "ios_abc123_1234567890",
    "deviceName": "iPhone 15",
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
  "deviceId": "ios_abc123_1234567890",
  "trustedAt": "2024-01-15T10:30:00Z"
}
```

---

### 5. Get Trusted Devices

```bash
curl -X GET http://localhost:8080/api/auth/devices/trust \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "devices": [
    {
      "id": 1,
      "deviceId": "ios_abc123_1234567890",
      "deviceName": "iPhone 15",
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

### 6. Revoke a Specific Session

```bash
curl -X DELETE http://localhost:8080/api/auth/sessions/1 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Session revoked successfully"
}
```

---

### 7. Revoke All Other Sessions

```bash
curl -X POST http://localhost:8080/api/auth/sessions/revoke-all \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "exceptDeviceId": "ios_abc123_1234567890"
  }'
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Sessions revoked successfully",
  "revokedCount": 3
}
```

---

### 8. Untrust a Device

```bash
curl -X DELETE http://localhost:8080/api/auth/devices/trust/ios_abc123_1234567890 \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

**Expected Response:**
```json
{
  "success": true,
  "message": "Device trust removed successfully"
}
```

---

## 🧪 Testing with Postman

### Collection Variables

| Variable | Value |
|----------|-------|
| `baseUrl` | `http://localhost:8080` |
| `accessToken` | (Set after login) |

### Request Headers

For authenticated requests, add:
- `Authorization`: `Bearer {{accessToken}}`
- `Content-Type`: `application/json`
- `X-Device-Id`: `your-device-id` (optional, for session tracking)

---

## 📱 Testing from React Native App

### 1. Test Session List Screen

Navigate to **Account Security Screen** (`AccountSecurityScreen.jsx`):

1. Open the app and login
2. Go to Profile → Account Security
3. The screen should load and display:
   - Current session (marked with "Current")
   - Other sessions (if logged in on other devices)
   - "Sign out from all devices" button

### 2. Test Session Revocation

1. Login from multiple devices (or simulators)
2. Open Account Security on one device
3. You should see multiple sessions
4. Tap "Revoke" on a session
5. That device should be logged out

### 3. Test "Sign Out All Devices"

1. Login from multiple devices
2. Tap "Sign out from all devices"
3. Current device stays logged in
4. All other devices get logged out

### 4. Test Device Trust

1. Navigate to "Trusted Devices" section
2. Tap "Trust This Device"
3. The device appears in trusted list
4. Tap to remove trust

---

## 🔍 Database Verification

### Check Sessions in PostgreSQL

```sql
-- View all sessions
SELECT 
  us.id,
  u.email,
  us.device_id,
  us.device_name,
  us.platform,
  us.is_active,
  us.is_trusted,
  us.last_activity_at
FROM user_sessions us
JOIN users u ON us.user_id = u.id
ORDER BY us.last_activity_at DESC;
```

### Check Trusted Devices

```sql
-- View trusted devices
SELECT 
  td.id,
  u.email,
  td.device_id,
  td.device_name,
  td.platform,
  td.is_active,
  td.trusted_at,
  td.last_used_at
FROM trusted_devices td
JOIN users u ON td.user_id = u.id
ORDER BY td.trusted_at DESC;
```

---

## ⚠️ Troubleshooting

### 1. 401 Unauthorized

**Cause:** Token expired or invalid

**Fix:** 
- Refresh token: `POST /api/auth/refresh`
- Re-login if refresh fails

### 2. Session Not Found

**Cause:** Session already revoked or belongs to another user

**Fix:** Refresh session list

### 3. Device Trust Fails

**Cause:** Missing device info

**Fix:** Ensure all required fields are sent:
- `deviceId` (required)
- `deviceName`
- `platform`
- `deviceModel`

### 4. CORS Errors (Browser Testing)

**Cause:** Java backend CORS config

**Fix:** Ensure CORS allows origin:
```java
@CrossOrigin(origins = "*")
```

---

## 📊 Test Scenarios Checklist

| Scenario | Status |
|----------|--------|
| Login creates session | ⬜ |
| Get sessions returns current session | ⬜ |
| Multiple logins create multiple sessions | ⬜ |
| Revoke session invalidates token | ⬜ |
| Revoke all keeps current session | ⬜ |
| Trust device marks session trusted | ⬜ |
| Untrust device removes trust | ⬜ |
| Validate returns user info | ⬜ |
| Expired token returns 401 | ⬜ |
| Frontend displays sessions correctly | ⬜ |

---

## 🚀 Running the Backend

### Start Java Auth Backend

```bash
cd jarbac
./mvnw spring-boot:run
```

Or with specific profile:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Verify Endpoints

Health check:
```bash
curl http://localhost:8080/actuator/health
```

---

## 📝 Next Steps

After testing Phase 4:

1. ✅ Verify all session endpoints work
2. ✅ Test from multiple devices
3. ✅ Verify database entries
4. ✅ Test frontend integration
5. 🔜 Proceed to Phase 5 (if applicable)

---

## 📁 Files Created/Modified

### Java Auth Backend

**New Files:**
- `entity/UserSession.java` - Session entity
- `entity/TrustedDevice.java` - Trusted device entity
- `repository/UserSessionRepository.java` - Session repository
- `repository/TrustedDeviceRepository.java` - Device repository
- `dto/SessionResponse.java` - Session API response
- `dto/DeviceInfoRequest.java` - Device info DTO
- `dto/RevokeAllSessionsRequest.java` - Revoke request DTO
- `service/SessionService.java` - Business logic
- `controller/SessionController.java` - REST endpoints

### Frontend (Already Created in Phase 4)

- `src/services/authInfraService.js` - Auth infrastructure service
- `src/screens/AccountSecurityScreen.jsx` - Security settings UI
- `src/config/api.js` - Updated with session endpoints

---

*Document Version: 1.0.0*
*Last Updated: Phase 4 Completion*
