# FixHomi Auth Service - Node.js Integration Guide

> **Complete integration guide for Node.js/Express backend developers**  
> **Last Updated:** December 2024  
> **Auth Service Version:** 1.1.0

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Environment Setup](#environment-setup)
4. [JWT Token Structure](#jwt-token-structure)
5. [API Reference](#api-reference)
   - [Authentication APIs](#authentication-apis)
   - [Passwordless OTP Login APIs](#passwordless-otp-login-apis) 🆕
   - [Token Management APIs](#token-management-apis)
   - [User Management APIs](#user-management-apis)
   - [Verification APIs](#verification-apis)
   - [Password Reset APIs](#password-reset-apis)
   - [Admin APIs](#admin-apis)
   - [OAuth2 APIs](#oauth2-apis)
6. [JWT Verification in Node.js](#jwt-verification-in-nodejs)
7. [Express Middleware Examples](#express-middleware-examples)
8. [Error Handling](#error-handling)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Overview

The FixHomi Auth Service is a **stateless JWT-based authentication microservice** built with Spring Boot. It handles:

- User registration and login
- **Passwordless OTP Login (Phone & Email)** 🆕
- JWT access token generation (24-hour expiry)
- Refresh token management (7-day expiry)
- Email and phone verification
- Password reset flows
- Google OAuth2 authentication
- Role-based access control

**Your Node.js services DO NOT call the Auth Service for every request.** Instead, they verify JWT tokens locally using the shared secret key.

---

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  React Native   │────▶│  Auth Service    │────▶│   PostgreSQL    │
│      App        │     │  (Spring Boot)   │     │    Database     │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                        │
        │ JWT Token              │ Issues JWT
        ▼                        │
┌─────────────────┐              │
│  Node.js APIs   │◀─────────────┘
│ (Verify locally)│
└─────────────────┘
```

**Flow:**
1. User logs in via React Native → Auth Service
2. Auth Service returns JWT token
3. React Native includes JWT in all API requests
4. Node.js services verify JWT locally (no Auth Service call needed)

---

## Environment Setup

### Required Environment Variables

```bash
# .env file for your Node.js service

# JWT Configuration - MUST MATCH Auth Service
JWT_SECRET=your-256-bit-secret-key-must-match-spring-boot-service
JWT_ISSUER=fixhomi-auth-service

# Auth Service URL (only needed for specific operations)
AUTH_SERVICE_URL=http://localhost:8080

# Your service configuration
PORT=3000
NODE_ENV=development
```

### Install Required Packages

```bash
npm install jsonwebtoken axios dotenv express
# or
yarn add jsonwebtoken axios dotenv express
```

---

## JWT Token Structure

Every JWT token from the Auth Service contains:

```json
{
  "userId": 123,
  "role": "USER",
  "tokenType": "ACCESS",
  "sub": "user@example.com",
  "iat": 1703001600,
  "exp": 1703088000,
  "iss": "fixhomi-auth-service"
}
```

### Token Claims Explained

| Claim | Type | Description |
|-------|------|-------------|
| `userId` | number | Unique user ID in database |
| `role` | string | User role: `USER`, `SERVICE_PROVIDER`, `ADMIN`, `SUPPORT`, `IT_ADMIN` |
| `tokenType` | string | Always `ACCESS` for access tokens |
| `sub` | string | User's email address (JWT standard subject claim) |
| `iat` | number | Issued at timestamp (Unix epoch) |
| `exp` | number | Expiration timestamp (Unix epoch) |
| `iss` | string | Issuer: `fixhomi-auth-service` |

### User Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| `USER` | Regular customers | Access own data, create bookings |
| `SERVICE_PROVIDER` | Plumbers, electricians, etc. | Access own data, manage jobs |
| `ADMIN` | Full administrative access | All permissions |
| `SUPPORT` | Customer support staff | View users, handle tickets |
| `IT_ADMIN` | IT administrators | System management |

---

## API Reference

### Base URL

```
Development: http://localhost:8080
Production:  https://auth.fixhomi.com
```

### Common Headers

```http
Content-Type: application/json
Authorization: Bearer <access_token>  # For protected endpoints
```

---

### Authentication APIs

#### 1. Register User

Creates a new user account and returns tokens.

```http
POST /api/auth/register
```

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "password": "SecurePass123!",
  "fullName": "John Doe",
  "role": "USER"
}
```

**Validation Rules:**
- `email`: Required, valid email format, max 100 chars
- `phoneNumber`: Optional, valid phone format
- `password`: Required, min 8 chars, must contain uppercase, lowercase, digit, special char
- `fullName`: Required, 2-100 chars
- `role`: Required, must be `USER` or `SERVICE_PROVIDER` (public registration)

**Success Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Validation Error | Invalid input data |
| 409 | Conflict | Email or phone already exists |

**Node.js Example:**
```javascript
const axios = require('axios');

async function registerUser(userData) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/register`,
      {
        email: userData.email,
        phoneNumber: userData.phone,
        password: userData.password,
        fullName: userData.name,
        role: 'USER'
      }
    );
    
    return {
      success: true,
      user: {
        id: response.data.userId,
        email: response.data.email,
        name: response.data.fullName,
        role: response.data.role
      },
      tokens: {
        accessToken: response.data.accessToken,
        refreshToken: response.data.refreshToken,
        expiresIn: response.data.expiresIn
      }
    };
  } catch (error) {
    if (error.response?.status === 409) {
      throw new Error('Email or phone number already registered');
    }
    throw error;
  }
}
```

---

#### 2. Login

Authenticates user and returns tokens.

```http
POST /api/auth/login
```

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "password": "SecurePass123!"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 401 | Authentication Failed | Invalid email or password |
| 401 | Account Disabled | User account is disabled |

**Node.js Example:**
```javascript
async function loginUser(email, password) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login`,
      { email, password }
    );
    
    return {
      success: true,
      user: {
        id: response.data.userId,
        email: response.data.email,
        name: response.data.fullName,
        role: response.data.role
      },
      tokens: {
        accessToken: response.data.accessToken,
        refreshToken: response.data.refreshToken,
        expiresIn: response.data.expiresIn
      }
    };
  } catch (error) {
    if (error.response?.status === 401) {
      const message = error.response.data.message;
      if (message.includes('disabled')) {
        throw new Error('Account is disabled. Please contact support.');
      }
      throw new Error('Invalid email or password');
    }
    throw error;
  }
}
```

---

#### 2b. Login with Phone Number 🆕

Authenticates user with phone number and password (alternative to email login).

```http
POST /api/auth/login/phone
```

**Request Body:**
```json
{
  "phoneNumber": "+1234567890",
  "password": "SecurePass123!"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 401 | Authentication Failed | Invalid phone number or password |
| 401 | Account Disabled | User account is disabled |
| 404 | User Not Found | No account found with this phone number |

**Node.js Example:**
```javascript
async function loginWithPhone(phoneNumber, password) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login/phone`,
      { phoneNumber, password }
    );
    
    return {
      success: true,
      user: {
        id: response.data.userId,
        email: response.data.email,
        name: response.data.fullName,
        role: response.data.role
      },
      tokens: {
        accessToken: response.data.accessToken,
        refreshToken: response.data.refreshToken,
        expiresIn: response.data.expiresIn
      }
    };
  } catch (error) {
    if (error.response?.status === 401) {
      const message = error.response.data.message;
      if (message.includes('disabled')) {
        throw new Error('Account is disabled. Please contact support.');
      }
      throw new Error('Invalid phone number or password');
    }
    if (error.response?.status === 404) {
      throw new Error('No account found with this phone number');
    }
    throw error;
  }
}
```

---

#### 3. Logout

Revokes the refresh token (access token remains valid until expiry).

```http
POST /api/auth/logout
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

**Node.js Example:**
```javascript
async function logoutUser(accessToken, refreshToken) {
  await axios.post(
    `${process.env.AUTH_SERVICE_URL}/api/auth/logout`,
    { refreshToken },
    {
      headers: { Authorization: `Bearer ${accessToken}` }
    }
  );
}
```

---

#### 4. Health Check

Check if Auth Service is running.

```http
GET /api/auth/health
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "message": "Auth service is running"
}
```

---

### Passwordless OTP Login APIs 🆕

> **New in v1.1.0:** Passwordless authentication using OTP sent to phone or email.

#### 5. Phone OTP Login - Send OTP

Send a 6-digit OTP to user's phone number for passwordless login.

```http
POST /api/auth/login/phone/send-otp
```

**Request Body:**
```json
{
  "phoneNumber": "+1234567890"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "OTP sent successfully to +123***7890",
  "maskedPhone": "+123***7890",
  "expiresInMinutes": 5
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 404 | Not Found | No user with this phone number |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Failed to send OTP |

**Node.js Example:**
```javascript
async function sendPhoneLoginOtp(phoneNumber) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login/phone/send-otp`,
      { phoneNumber }
    );
    return response.data;
  } catch (error) {
    if (error.response?.status === 404) {
      throw new Error('No account found with this phone number');
    }
    if (error.response?.status === 429) {
      throw new Error('Too many requests. Please wait before trying again.');
    }
    throw error;
  }
}
```

---

#### 6. Phone OTP Login - Verify OTP

Verify OTP and complete login. Returns JWT tokens on success.

```http
POST /api/auth/login/phone/verify
```

**Request Body:**
```json
{
  "phoneNumber": "+1234567890",
  "otp": "123456"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid OTP | OTP is incorrect |
| 400 | OTP Expired | OTP has expired |
| 429 | Too Many Attempts | Max verification attempts exceeded |

**Node.js Example:**
```javascript
async function verifyPhoneLoginOtp(phoneNumber, otp) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login/phone/verify`,
      { phoneNumber, otp }
    );
    return {
      success: true,
      user: {
        id: response.data.userId,
        email: response.data.email,
        name: response.data.fullName,
        role: response.data.role
      },
      tokens: {
        accessToken: response.data.accessToken,
        refreshToken: response.data.refreshToken,
        expiresIn: response.data.expiresIn
      }
    };
  } catch (error) {
    if (error.response?.status === 400) {
      throw new Error(error.response.data.message || 'Invalid or expired OTP');
    }
    throw error;
  }
}
```

---

#### 7. Email OTP Login - Send OTP

Send a 6-digit OTP to user's email for passwordless login.

```http
POST /api/auth/login/email/send-otp
```

**Request Body:**
```json
{
  "email": "john.doe@example.com"
}
```

**Success Response (200 OK):**
```json
{
  "success": true,
  "message": "OTP sent successfully to j***@example.com",
  "maskedEmail": "j***@example.com",
  "expiresInMinutes": 5
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 404 | Not Found | No user with this email |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Server Error | Failed to send OTP |

**Node.js Example:**
```javascript
async function sendEmailLoginOtp(email) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login/email/send-otp`,
      { email }
    );
    return response.data;
  } catch (error) {
    if (error.response?.status === 404) {
      throw new Error('No account found with this email address');
    }
    if (error.response?.status === 429) {
      throw new Error('Too many requests. Please wait before trying again.');
    }
    throw error;
  }
}
```

---

#### 8. Email OTP Login - Verify OTP

Verify OTP and complete login. Returns JWT tokens on success.

```http
POST /api/auth/login/email/verify
```

**Request Body:**
```json
{
  "email": "john.doe@example.com",
  "otp": "123456"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid OTP | OTP is incorrect |
| 400 | OTP Expired | OTP has expired |
| 429 | Too Many Attempts | Max verification attempts exceeded |

**Node.js Example:**
```javascript
async function verifyEmailLoginOtp(email, otp) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/login/email/verify`,
      { email, otp }
    );
    return {
      success: true,
      user: {
        id: response.data.userId,
        email: response.data.email,
        name: response.data.fullName,
        role: response.data.role
      },
      tokens: {
        accessToken: response.data.accessToken,
        refreshToken: response.data.refreshToken,
        expiresIn: response.data.expiresIn
      }
    };
  } catch (error) {
    if (error.response?.status === 400) {
      throw new Error(error.response.data.message || 'Invalid or expired OTP');
    }
    throw error;
  }
}
```

---

### Token Management APIs

#### 5. Refresh Token

Get a new access token using refresh token. Implements token rotation (old refresh token is revoked).

```http
POST /api/auth/refresh
```

**Request Body:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...<new_token>",
  "refreshToken": "660e8400-e29b-41d4-a716-446655440001",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 401 | Invalid Token | Refresh token not found or expired |
| 401 | Token Revoked | Refresh token was revoked |
| 401 | Account Disabled | User account is disabled |

**Node.js Example:**
```javascript
async function refreshTokens(refreshToken) {
  try {
    const response = await axios.post(
      `${process.env.AUTH_SERVICE_URL}/api/auth/refresh`,
      { refreshToken }
    );
    
    return {
      accessToken: response.data.accessToken,
      refreshToken: response.data.refreshToken,  // New refresh token!
      expiresIn: response.data.expiresIn
    };
  } catch (error) {
    // Force user to login again
    throw new Error('Session expired. Please login again.');
  }
}
```

---

#### 6. Validate Token

Validates an access token and returns user info. **Use this sparingly** - prefer local JWT verification.

```http
GET /api/auth/token/validate
Authorization: Bearer <access_token>
```

**Success Response (200 OK):**
```json
{
  "valid": true,
  "userId": 1,
  "email": "john.doe@example.com",
  "role": "USER",
  "expiresAt": "2024-12-20T10:30:00"
}
```

**Error Response (401):**
```json
{
  "valid": false,
  "error": "Token expired"
}
```

---

#### 7. Get Current User from Token

Returns full user info for the authenticated user.

```http
GET /api/auth/token/me
Authorization: Bearer <access_token>
```

**Success Response (200 OK):**
```json
{
  "userId": 1,
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "phoneNumber": "+1234567890",
  "emailVerified": true,
  "phoneVerified": false,
  "active": true,
  "createdAt": "2024-12-14T10:30:00"
}
```

---

### User Management APIs

#### 8. Get User Profile

Returns the authenticated user's full profile.

```http
GET /api/users/me
Authorization: Bearer <access_token>
```

**Success Response (200 OK):**
```json
{
  "id": 1,
  "email": "john.doe@example.com",
  "phoneNumber": "+1234567890",
  "fullName": "John Doe",
  "role": "USER",
  "active": true,
  "emailVerified": true,
  "phoneVerified": false,
  "createdAt": "2024-12-14T10:30:00",
  "updatedAt": "2024-12-14T12:00:00",
  "lastLoginAt": "2024-12-14T15:30:00"
}
```

---

#### 9. Change Password

Changes the authenticated user's password.

```http
POST /api/users/me/change-password
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "currentPassword": "OldPassword123!",
  "newPassword": "NewSecurePass456!"
}
```

**Validation Rules:**
- `currentPassword`: Required, must match current password
- `newPassword`: Required, min 8 chars, must contain uppercase, lowercase, digit, special char

**Success Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Validation Error | Password doesn't meet requirements |
| 401 | Invalid Password | Current password is incorrect |

---

### Verification APIs

#### 10. Send Phone OTP

Sends a 6-digit OTP to the user's phone for verification.

```http
POST /api/verification/otp/send
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "phoneNumber": "+1234567890"
}
```

**Success Response (200 OK):**
```json
{
  "message": "OTP sent successfully",
  "expiresInSeconds": 300
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid Phone | Invalid phone number format |
| 429 | Too Many Requests | Rate limit exceeded (wait before retry) |

**Note:** In development, OTP is logged to console. In production, integrates with Twilio.

---

#### 11. Verify Phone OTP

Verifies the OTP and marks phone as verified.

```http
POST /api/verification/otp/verify
Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "phoneNumber": "+1234567890",
  "otp": "123456"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Phone verified successfully",
  "phoneVerified": true
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid OTP | OTP is incorrect |
| 400 | OTP Expired | OTP has expired (5 min limit) |
| 429 | Too Many Attempts | Max 3 attempts exceeded |

---

#### 12. Send Email Verification

Sends a verification link to the user's email.

```http
POST /api/verification/email/send-verification
Authorization: Bearer <access_token>
```

**Success Response (200 OK):**
```json
{
  "message": "Verification email sent",
  "expiresInHours": 24
}
```

---

#### 13. Verify Email Token

Verifies email using the token from the email link.

```http
GET /api/verification/email/verify?token=<verification_token>
```

**Success Response (200 OK):**
```json
{
  "message": "Email verified successfully",
  "emailVerified": true
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid Token | Token not found or already used |
| 400 | Token Expired | Token has expired (24 hour limit) |

---

### Password Reset APIs

#### 14. Forgot Password

Initiates password reset flow. Always returns 200 to prevent email enumeration.

```http
POST /api/verification/forgot-password
```

**Request Body:**
```json
{
  "email": "john.doe@example.com"
}
```

**Response (200 OK):** *(Always returns success)*
```json
{
  "message": "If an account exists with this email, a password reset link will be sent"
}
```

---

#### 15. Validate Reset Token

Validates if a password reset token is still valid.

```http
GET /api/verification/reset-password/validate?token=<reset_token>
```

**Success Response (200 OK):**
```json
{
  "valid": true,
  "email": "john.doe@example.com"
}
```

**Error Response (400):**
```json
{
  "valid": false,
  "message": "Token expired or invalid"
}
```

---

#### 16. Reset Password

Resets password using the token from email.

```http
POST /api/verification/reset-password
```

**Request Body:**
```json
{
  "token": "reset-token-from-email",
  "newPassword": "NewSecurePass789!"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Password reset successfully"
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 400 | Invalid Token | Token not found or expired |
| 400 | Validation Error | Password doesn't meet requirements |

---

### Admin APIs

**Note:** These endpoints require `ADMIN`, `IT_ADMIN`, or `SUPPORT` role.

#### 17. Create User (Admin)

Creates a user with any role (including admin roles).

```http
POST /api/admin/users
Authorization: Bearer <admin_access_token>
```

**Request Body:**
```json
{
  "email": "support@fixhomi.com",
  "phoneNumber": "+1234567890",
  "password": "TempPassword123!",
  "fullName": "Support Agent",
  "role": "SUPPORT"
}
```

**Allowed Roles:** `USER`, `SERVICE_PROVIDER`, `ADMIN`, `SUPPORT`, `IT_ADMIN`

**Success Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 5,
  "email": "support@fixhomi.com",
  "fullName": "Support Agent",
  "role": "SUPPORT",
  "expiresIn": 86400
}
```

---

#### 18. Update User Status (Admin)

Enable or disable a user account.

```http
PATCH /api/admin/users/{userId}/status
Authorization: Bearer <admin_access_token>
```

**Request Body:**
```json
{
  "active": false
}
```

**Success Response (200 OK):**
```json
{
  "id": 3,
  "email": "banned.user@example.com",
  "fullName": "Banned User",
  "role": "USER",
  "active": false,
  "message": "User status updated successfully"
}
```

**Error Responses:**

| Status | Error | Description |
|--------|-------|-------------|
| 403 | Forbidden | Not an admin user |
| 404 | Not Found | User ID doesn't exist |

---

### OAuth2 APIs

#### 19. Google OAuth2 Login

Initiates Google OAuth2 flow.

```http
GET /oauth2/authorization/google
```

**Flow:**
1. Redirect user to this URL
2. User authenticates with Google
3. Auth Service handles callback
4. Redirects to frontend with tokens

**Callback URL:** `/api/auth/oauth2/callback/google`

**Frontend Redirect:** `{FRONTEND_URL}/oauth-callback?token=<jwt>&refresh=<refresh_token>`

---

## JWT Verification in Node.js

### Complete JWT Verification Module

Create `auth/jwtVerifier.js`:

```javascript
const jwt = require('jsonwebtoken');

// Configuration - MUST match Spring Boot Auth Service
const JWT_CONFIG = {
  secret: process.env.JWT_SECRET,
  issuer: process.env.JWT_ISSUER || 'fixhomi-auth-service',
  algorithms: ['HS512']
};

/**
 * Verifies a JWT token and returns decoded payload
 * @param {string} token - JWT token without 'Bearer ' prefix
 * @returns {Object} Decoded token payload
 * @throws {Error} If token is invalid
 */
function verifyToken(token) {
  try {
    const decoded = jwt.verify(token, JWT_CONFIG.secret, {
      issuer: JWT_CONFIG.issuer,
      algorithms: JWT_CONFIG.algorithms
    });
    
    // Validate required claims
    if (!decoded.userId || !decoded.role || !decoded.sub) {
      throw new Error('Token missing required claims');
    }
    
    if (decoded.tokenType !== 'ACCESS') {
      throw new Error('Invalid token type');
    }
    
    return {
      userId: decoded.userId,
      email: decoded.sub,
      role: decoded.role,
      issuedAt: new Date(decoded.iat * 1000),
      expiresAt: new Date(decoded.exp * 1000)
    };
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      throw new Error('Token expired');
    }
    if (error.name === 'JsonWebTokenError') {
      throw new Error('Invalid token');
    }
    throw error;
  }
}

/**
 * Extracts token from Authorization header
 * @param {string} authHeader - Full Authorization header value
 * @returns {string|null} Token or null if invalid format
 */
function extractToken(authHeader) {
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }
  return authHeader.substring(7);
}

/**
 * Checks if user has required role
 * @param {string} userRole - User's role from token
 * @param {string[]} allowedRoles - Array of allowed roles
 * @returns {boolean}
 */
function hasRole(userRole, allowedRoles) {
  return allowedRoles.includes(userRole);
}

/**
 * Role hierarchy for permission checking
 */
const ROLE_HIERARCHY = {
  'IT_ADMIN': ['IT_ADMIN', 'ADMIN', 'SUPPORT', 'SERVICE_PROVIDER', 'USER'],
  'ADMIN': ['ADMIN', 'SUPPORT', 'SERVICE_PROVIDER', 'USER'],
  'SUPPORT': ['SUPPORT', 'SERVICE_PROVIDER', 'USER'],
  'SERVICE_PROVIDER': ['SERVICE_PROVIDER', 'USER'],
  'USER': ['USER']
};

/**
 * Checks if user role has at least the minimum required role level
 * @param {string} userRole - User's role
 * @param {string} minimumRole - Minimum required role
 * @returns {boolean}
 */
function hasMinimumRole(userRole, minimumRole) {
  const allowedRoles = ROLE_HIERARCHY[userRole] || [];
  return allowedRoles.includes(minimumRole);
}

module.exports = {
  verifyToken,
  extractToken,
  hasRole,
  hasMinimumRole,
  ROLE_HIERARCHY
};
```

---

## Express Middleware Examples

### Basic Authentication Middleware

```javascript
const { verifyToken, extractToken } = require('./auth/jwtVerifier');

/**
 * Authentication middleware - verifies JWT token
 */
function authenticate(req, res, next) {
  const token = extractToken(req.headers.authorization);
  
  if (!token) {
    return res.status(401).json({
      error: 'Unauthorized',
      message: 'No token provided'
    });
  }
  
  try {
    const user = verifyToken(token);
    req.user = user;
    next();
  } catch (error) {
    return res.status(401).json({
      error: 'Unauthorized',
      message: error.message
    });
  }
}

module.exports = { authenticate };
```

### Role-Based Authorization Middleware

```javascript
const { hasRole, hasMinimumRole } = require('./auth/jwtVerifier');

/**
 * Requires specific roles
 * @param {...string} roles - Allowed roles
 */
function requireRoles(...roles) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Authentication required'
      });
    }
    
    if (!hasRole(req.user.role, roles)) {
      return res.status(403).json({
        error: 'Forbidden',
        message: `Access denied. Required roles: ${roles.join(', ')}`
      });
    }
    
    next();
  };
}

/**
 * Requires minimum role level
 * @param {string} minimumRole - Minimum required role
 */
function requireMinimumRole(minimumRole) {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        error: 'Unauthorized',
        message: 'Authentication required'
      });
    }
    
    if (!hasMinimumRole(req.user.role, minimumRole)) {
      return res.status(403).json({
        error: 'Forbidden',
        message: `Access denied. Minimum role required: ${minimumRole}`
      });
    }
    
    next();
  };
}

module.exports = { requireRoles, requireMinimumRole };
```

### Complete Express App Example

```javascript
const express = require('express');
const { authenticate } = require('./middleware/auth');
const { requireRoles, requireMinimumRole } = require('./middleware/roles');

const app = express();
app.use(express.json());

// Public routes - no authentication
app.get('/api/health', (req, res) => {
  res.json({ status: 'UP' });
});

// Protected routes - any authenticated user
app.get('/api/profile', authenticate, (req, res) => {
  res.json({
    message: 'Your profile',
    user: req.user
  });
});

// Service provider only routes
app.get('/api/my-jobs', 
  authenticate, 
  requireRoles('SERVICE_PROVIDER'), 
  (req, res) => {
    res.json({
      message: `Jobs for service provider ${req.user.userId}`
    });
  }
);

// Admin routes
app.get('/api/admin/users',
  authenticate,
  requireRoles('ADMIN', 'IT_ADMIN', 'SUPPORT'),
  (req, res) => {
    res.json({
      message: 'User list',
      requestedBy: req.user.email
    });
  }
);

// Support and above can access
app.get('/api/tickets',
  authenticate,
  requireMinimumRole('SUPPORT'),
  (req, res) => {
    res.json({ message: 'Support tickets' });
  }
);

// Owner-only routes (user can only access their own data)
app.get('/api/users/:userId/orders',
  authenticate,
  (req, res) => {
    const requestedUserId = parseInt(req.params.userId);
    
    // Check if user is accessing their own data or is admin
    if (req.user.userId !== requestedUserId && 
        !hasRole(req.user.role, ['ADMIN', 'IT_ADMIN', 'SUPPORT'])) {
      return res.status(403).json({
        error: 'Forbidden',
        message: 'You can only access your own orders'
      });
    }
    
    res.json({
      userId: requestedUserId,
      orders: []
    });
  }
);

app.listen(3000, () => {
  console.log('Server running on port 3000');
});
```

---

## Error Handling

### Standard Error Response Format

All Auth Service errors follow this format:

```json
{
  "timestamp": "2024-12-14T10:30:00",
  "status": 401,
  "error": "Authentication Failed",
  "message": "Invalid email or password",
  "path": "/api/auth/login",
  "validationErrors": {
    "email": "must be a valid email",
    "password": "must be at least 8 characters"
  }
}
```

### Error Handler for Auth Service Calls

```javascript
const axios = require('axios');

// Create axios instance with interceptors
const authServiceClient = axios.create({
  baseURL: process.env.AUTH_SERVICE_URL,
  timeout: 10000
});

// Response interceptor for error handling
authServiceClient.interceptors.response.use(
  response => response,
  error => {
    if (!error.response) {
      // Network error or Auth Service down
      const customError = new Error('Auth service unavailable');
      customError.statusCode = 503;
      throw customError;
    }
    
    const { status, data } = error.response;
    
    // Create standardized error
    const customError = new Error(data.message || 'Authentication error');
    customError.statusCode = status;
    customError.validationErrors = data.validationErrors;
    
    throw customError;
  }
);

module.exports = authServiceClient;
```

### Express Global Error Handler

```javascript
app.use((error, req, res, next) => {
  console.error('Error:', error);
  
  const statusCode = error.statusCode || 500;
  
  res.status(statusCode).json({
    error: statusCode === 500 ? 'Internal Server Error' : error.message,
    message: error.message,
    ...(error.validationErrors && { validationErrors: error.validationErrors }),
    timestamp: new Date().toISOString(),
    path: req.path
  });
});
```

---

## Best Practices

### 1. Token Storage
```javascript
// Store tokens securely - never in localStorage for sensitive apps
// Use httpOnly cookies or secure storage

// For API-to-API communication, use environment variables
const serviceToken = process.env.SERVICE_AUTH_TOKEN;
```

### 2. Token Refresh Strategy
```javascript
// Implement automatic token refresh before expiry
const TOKEN_REFRESH_THRESHOLD = 5 * 60 * 1000; // 5 minutes before expiry

function shouldRefreshToken(expiresAt) {
  return new Date(expiresAt).getTime() - Date.now() < TOKEN_REFRESH_THRESHOLD;
}
```

### 3. Graceful Auth Service Failures
```javascript
// Cache user data for resilience
const userCache = new Map();

async function getUserWithFallback(userId, token) {
  try {
    const user = await authServiceClient.get('/api/auth/token/me', {
      headers: { Authorization: `Bearer ${token}` }
    });
    userCache.set(userId, user.data);
    return user.data;
  } catch (error) {
    // Return cached data if Auth Service is down
    if (userCache.has(userId)) {
      console.warn('Using cached user data');
      return userCache.get(userId);
    }
    throw error;
  }
}
```

### 4. Rate Limiting Awareness
```javascript
// Handle 429 Too Many Requests
if (error.response?.status === 429) {
  const retryAfter = error.response.headers['retry-after'] || 60;
  // Wait and retry or inform user
}
```

### 5. Logging Best Practices
```javascript
// Log authentication events (without sensitive data)
function logAuthEvent(event, userId, success) {
  console.log(JSON.stringify({
    timestamp: new Date().toISOString(),
    event: event,
    userId: userId,
    success: success,
    // Never log passwords or tokens!
  }));
}
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Invalid signature" | JWT secret mismatch | Ensure `JWT_SECRET` matches exactly |
| "Token expired" | Access token expired | Use refresh token to get new access token |
| "Invalid token type" | Using refresh token as access token | Use correct token type |
| 403 on admin endpoints | User doesn't have required role | Check user role |
| Network errors | Auth Service down or unreachable | Check service health, implement fallbacks |

### Debug Token Issues

```javascript
// Decode token without verification (for debugging)
const jwt = require('jsonwebtoken');

function debugToken(token) {
  const decoded = jwt.decode(token, { complete: true });
  console.log('Header:', decoded.header);
  console.log('Payload:', decoded.payload);
  console.log('Expires:', new Date(decoded.payload.exp * 1000));
  console.log('Is Expired:', Date.now() > decoded.payload.exp * 1000);
}
```

### Verify Secret Key Format

```javascript
// The secret must be the same string used in Spring Boot
// Check application.yaml: jwt.secret value
console.log('JWT Secret length:', process.env.JWT_SECRET?.length);
console.log('JWT Secret (first 10 chars):', process.env.JWT_SECRET?.substring(0, 10));
```

---

## Quick Reference Card

| Operation | Endpoint | Method | Auth Required |
|-----------|----------|--------|---------------|
| Register | `/api/auth/register` | POST | No |
| Login (Email) | `/api/auth/login` | POST | No |
| Login (Phone) 🆕 | `/api/auth/login/phone` | POST | No |
| Logout | `/api/auth/logout` | POST | Yes |
| Refresh Token | `/api/auth/refresh` | POST | No |
| Validate Token | `/api/auth/token/validate` | GET | Yes |
| Get User from Token | `/api/auth/token/me` | GET | Yes |
| Get Profile | `/api/users/me` | GET | Yes |
| Change Password | `/api/users/me/change-password` | POST | Yes |
| Send Phone OTP | `/api/verification/otp/send` | POST | Yes |
| Verify Phone OTP | `/api/verification/otp/verify` | POST | Yes |
| Send Email Verification | `/api/verification/email/send-verification` | POST | Yes |
| Verify Email | `/api/verification/email/verify` | GET | No |
| Forgot Password | `/api/verification/forgot-password` | POST | No |
| Validate Reset Token | `/api/verification/reset-password/validate` | GET | No |
| Reset Password | `/api/verification/reset-password` | POST | No |
| Create User (Admin) | `/api/admin/users` | POST | Yes (Admin) |
| Update User Status | `/api/admin/users/{id}/status` | PATCH | Yes (Admin) |
| Google OAuth | `/oauth2/authorization/google` | GET | No |

---

**Questions?** Contact the FixHomi Engineering Team.
