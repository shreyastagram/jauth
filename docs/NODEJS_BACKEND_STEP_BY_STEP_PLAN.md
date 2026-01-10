# FixHomi Auth Service - Node.js Backend Integration Plan

> **Step-by-step integration guide for Node.js/Express backend services**  
> **Auth Service Version:** 1.2.0  
> **Last Updated:** December 2024

---

## 📱 Overview

This guide covers the **Node.js backend integration** with FixHomi Auth Service. Your Node.js backend acts as a **consumer** of the Auth Service, NOT as an auth provider.

### Architecture Understanding

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        FIXHOMI ARCHITECTURE                                  │
└─────────────────────────────────────────────────────────────────────────────┘

    MOBILE APP (React Native)              NODE.JS BACKEND              JAVA AUTH SERVICE
            │                                    │                            │
            │  1. Login (email/password)         │                            │
            │────────────────────────────────────┼───────────────────────────▶│
            │                                    │                            │
            │  2. Returns tokens + user          │                            │
            │◀───────────────────────────────────┼────────────────────────────│
            │                                    │                            │
            │  3. API call with JWT              │                            │
            │───────────────────────────────────▶│                            │
            │                                    │  4. Validate JWT           │
            │                                    │───────────────────────────▶│
            │                                    │                            │
            │                                    │  5. Returns user info      │
            │                                    │◀───────────────────────────│
            │                                    │                            │
            │                                    │  6. Process request        │
            │  7. Return business data           │                            │
            │◀───────────────────────────────────│                            │
            │                                    │                            │
```

### What Mobile App Does vs What Node.js Does

| Actor | Calls Auth Service | Purpose |
|-------|-------------------|---------|
| **Mobile App** | Login, Register, Logout, Refresh, OTP, Password Reset | **User authentication** |
| **Node.js Backend** | Validate Token, Get User from Token | **Token validation only** |

---

## 🎯 API Endpoints for Node.js Backend

### Only 3 APIs You Need

| # | API | Method | Purpose | When to Use |
|---|-----|--------|---------|-------------|
| 1 | `/api/token/validate` | GET | Validate JWT token | Every protected API request |
| 2 | `/api/token/me` | GET | Get user info from JWT | When you need user details |
| 3 | `/api/auth/health` | GET | Check Auth Service status | Health checks, startup |

### APIs Node.js Does NOT Call (Mobile App Only)

| API | Purpose | Why Node.js Doesn't Need |
|-----|---------|-------------------------|
| `/api/auth/login` | Login with email+password | Mobile app handles login |
| `/api/auth/login/phone` | Login with phone+password | Mobile app handles login |
| `/api/auth/register` | Register new user | Mobile app handles registration |
| `/api/auth/logout` | Revoke refresh token | Mobile app handles logout |
| `/api/auth/refresh` | Refresh access token | Mobile app handles refresh |
| `/api/auth/login/phone/*` | Phone OTP login | Mobile app handles OTP |
| `/api/auth/login/email/*` | Email OTP login | Mobile app handles OTP |
| `/api/auth/otp/*` | Phone verification | Mobile app handles verification |
| `/api/auth/email/*` | Email verification | Mobile app handles verification |
| `/api/auth/forgot-password` | Password reset | Mobile app handles password reset |
| `/api/auth/reset-password` | Reset password | Mobile app handles password reset |
| `/oauth2/authorization/google` | Google OAuth | Mobile app handles OAuth |

---

## 🔄 How Node.js Uses Auth Service

### Token Validation Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    NODE.JS TOKEN VALIDATION FLOW                             │
└─────────────────────────────────────────────────────────────────────────────┘

    MOBILE APP                NODE.JS BACKEND                AUTH SERVICE
         │                          │                             │
         │  1. Request with JWT     │                             │
         │      Authorization:      │                             │
         │      Bearer eyJ...       │                             │
         │─────────────────────────▶│                             │
         │                          │                             │
         │                          │  2. GET /api/token/validate │
         │                          │     Authorization:          │
         │                          │     Bearer eyJ...           │
         │                          │────────────────────────────▶│
         │                          │                             │
         │                          │  3. Token validation result │
         │                          │     {valid: true, userId,   │
         │                          │      email, role, ...}      │
         │                          │◀────────────────────────────│
         │                          │                             │
         │                          │  4. If valid: process req   │
         │                          │     If invalid: return 401  │
         │                          │                             │
         │  5. Business response    │                             │
         │◀─────────────────────────│                             │
         │                          │                             │
```

---

## 📋 Step-by-Step Integration Plan

### Phase 1: Project Setup (Day 1)

#### Step 1.1: Initialize Project Structure

```
your-nodejs-backend/
├── src/
│   ├── config/
│   │   └── env.js              # Environment configuration
│   ├── middleware/
│   │   └── authMiddleware.js   # JWT validation middleware
│   ├── services/
│   │   └── authService.js      # Auth Service API client
│   ├── routes/
│   │   └── ...                 # Your business routes
│   └── app.js
├── .env
├── .env.example
└── package.json
```

#### Step 1.2: Install Dependencies

```bash
npm install express axios dotenv
npm install --save-dev nodemon
```

#### Step 1.3: Create Environment Configuration

**File:** `.env.example`

```bash
# Auth Service Configuration
AUTH_SERVICE_URL=http://localhost:8080
AUTH_SERVICE_TIMEOUT=5000
AUTH_SERVICE_RETRY_COUNT=3

# Node.js Server Configuration
PORT=3000
NODE_ENV=development
```

**File:** `.env` (copy and modify)

```bash
AUTH_SERVICE_URL=http://localhost:8080
AUTH_SERVICE_TIMEOUT=5000
AUTH_SERVICE_RETRY_COUNT=3
PORT=3000
NODE_ENV=development
```

#### Step 1.4: Environment Config Module

**File:** `src/config/env.js`

```javascript
require('dotenv').config();

module.exports = {
  // Auth Service
  authService: {
    baseUrl: process.env.AUTH_SERVICE_URL || 'http://localhost:8080',
    timeout: parseInt(process.env.AUTH_SERVICE_TIMEOUT) || 5000,
    retryCount: parseInt(process.env.AUTH_SERVICE_RETRY_COUNT) || 3,
  },
  
  // Server
  server: {
    port: parseInt(process.env.PORT) || 3000,
    env: process.env.NODE_ENV || 'development',
  },
};
```

---

### Phase 2: Auth Service Client (Day 2)

#### Step 2.1: Create Auth Service Client

**File:** `src/services/authService.js`

```javascript
const axios = require('axios');
const config = require('../config/env');

// Create Axios instance for Auth Service
const authClient = axios.create({
  baseURL: config.authService.baseUrl,
  timeout: config.authService.timeout,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Auth Service API Client
 * 
 * This service communicates with FixHomi Java Auth Service
 * to validate tokens and get user information.
 */
const authService = {
  /**
   * Validate JWT Token
   * 
   * API: GET /api/token/validate
   * 
   * Response (200):
   * {
   *   "valid": true,
   *   "userId": 1,
   *   "email": "user@example.com",
   *   "role": "USER",
   *   "tokenType": "ACCESS",
   *   "issuedAt": 1734600000,
   *   "expiresAt": 1734686400
   * }
   * 
   * Invalid Token Response (200):
   * {
   *   "valid": false
   * }
   */
  async validateToken(accessToken) {
    try {
      const response = await authClient.get('/api/token/validate', {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
        },
      });
      return response.data;
    } catch (error) {
      console.error('Token validation error:', error.message);
      // Network error or Auth Service down
      if (error.code === 'ECONNREFUSED') {
        throw new Error('Auth Service unavailable');
      }
      return { valid: false };
    }
  },

  /**
   * Get User Info from JWT Token
   * 
   * API: GET /api/token/me
   * 
   * Response (200):
   * {
   *   "valid": true,
   *   "userId": 1,
   *   "email": "user@example.com",
   *   "role": "USER",
   *   "tokenType": "ACCESS",
   *   "issuedAt": 1734600000,
   *   "expiresAt": 1734686400
   * }
   */
  async getCurrentUser(accessToken) {
    try {
      const response = await authClient.get('/api/token/me', {
        headers: {
          'Authorization': `Bearer ${accessToken}`,
        },
      });
      return response.data;
    } catch (error) {
      console.error('Get user error:', error.message);
      if (error.response?.status === 401) {
        return null;
      }
      throw error;
    }
  },

  /**
   * Health Check
   * 
   * API: GET /api/auth/health
   * 
   * Response (200):
   * {
   *   "status": "UP",
   *   "timestamp": "2024-12-19T10:30:00Z"
   * }
   */
  async healthCheck() {
    try {
      const response = await authClient.get('/api/auth/health');
      return {
        healthy: true,
        data: response.data,
      };
    } catch (error) {
      return {
        healthy: false,
        error: error.message,
      };
    }
  },
};

module.exports = authService;
```

---

### Phase 3: Authentication Middleware (Day 3)

#### Step 3.1: Create Auth Middleware

**File:** `src/middleware/authMiddleware.js`

```javascript
const authService = require('../services/authService');

/**
 * JWT Authentication Middleware
 * 
 * Validates JWT token from Authorization header by calling
 * FixHomi Auth Service's /api/token/validate endpoint.
 * 
 * On success: Adds req.user with user info
 * On failure: Returns 401 Unauthorized
 */
const authenticate = async (req, res, next) => {
  try {
    // Step 1: Extract token from Authorization header
    const authHeader = req.headers.authorization;
    
    if (!authHeader) {
      return res.status(401).json({
        success: false,
        message: 'No authorization header provided',
      });
    }
    
    if (!authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        success: false,
        message: 'Invalid authorization header format. Expected: Bearer <token>',
      });
    }
    
    const token = authHeader.substring(7); // Remove "Bearer " prefix
    
    if (!token) {
      return res.status(401).json({
        success: false,
        message: 'No token provided',
      });
    }
    
    // Step 2: Validate token with Auth Service
    const validationResult = await authService.validateToken(token);
    
    // Step 3: Check validation result
    if (!validationResult.valid) {
      return res.status(401).json({
        success: false,
        message: 'Invalid or expired token',
      });
    }
    
    // Step 4: Attach user info to request
    req.user = {
      id: validationResult.userId,
      email: validationResult.email,
      role: validationResult.role,
      tokenType: validationResult.tokenType,
      issuedAt: validationResult.issuedAt,
      expiresAt: validationResult.expiresAt,
    };
    
    // Step 5: Continue to next middleware/route
    next();
    
  } catch (error) {
    console.error('Authentication middleware error:', error.message);
    
    if (error.message === 'Auth Service unavailable') {
      return res.status(503).json({
        success: false,
        message: 'Authentication service temporarily unavailable',
      });
    }
    
    return res.status(500).json({
      success: false,
      message: 'Internal server error during authentication',
    });
  }
};

/**
 * Role-based Authorization Middleware
 * 
 * Must be used AFTER authenticate middleware.
 * 
 * Usage:
 *   router.get('/admin', authenticate, authorize(['ADMIN', 'IT_ADMIN']), handler);
 *   router.get('/user', authenticate, authorize(['USER', 'SERVICE_PROVIDER']), handler);
 */
const authorize = (allowedRoles) => {
  return (req, res, next) => {
    if (!req.user) {
      return res.status(401).json({
        success: false,
        message: 'Authentication required',
      });
    }
    
    if (!allowedRoles.includes(req.user.role)) {
      return res.status(403).json({
        success: false,
        message: 'Access denied. Insufficient permissions.',
      });
    }
    
    next();
  };
};

/**
 * Optional Authentication Middleware
 * 
 * Same as authenticate, but doesn't fail if no token provided.
 * Useful for endpoints that work for both authenticated and anonymous users.
 * 
 * If token provided and valid: req.user is populated
 * If no token or invalid: req.user is undefined (continues)
 */
const optionalAuth = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return next(); // Continue without user
    }
    
    const token = authHeader.substring(7);
    
    if (!token) {
      return next();
    }
    
    const validationResult = await authService.validateToken(token);
    
    if (validationResult.valid) {
      req.user = {
        id: validationResult.userId,
        email: validationResult.email,
        role: validationResult.role,
        tokenType: validationResult.tokenType,
        issuedAt: validationResult.issuedAt,
        expiresAt: validationResult.expiresAt,
      };
    }
    
    next();
    
  } catch (error) {
    // On error, continue without user
    console.error('Optional auth error:', error.message);
    next();
  }
};

module.exports = {
  authenticate,
  authorize,
  optionalAuth,
};
```

---

### Phase 4: Express Application Setup (Day 4)

#### Step 4.1: Main Application File

**File:** `src/app.js`

```javascript
const express = require('express');
const config = require('./config/env');
const authService = require('./services/authService');
const { authenticate, authorize, optionalAuth } = require('./middleware/authMiddleware');

const app = express();

// Middleware
app.use(express.json());

// =============================================================================
// HEALTH CHECK ENDPOINTS
// =============================================================================

/**
 * Node.js Backend Health Check
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'UP',
    service: 'FixHomi Node.js Backend',
    timestamp: new Date().toISOString(),
  });
});

/**
 * Auth Service Health Check
 * 
 * Calls: GET /api/auth/health
 */
app.get('/health/auth', async (req, res) => {
  const authHealth = await authService.healthCheck();
  
  res.json({
    authService: authHealth.healthy ? 'UP' : 'DOWN',
    details: authHealth.data || authHealth.error,
    timestamp: new Date().toISOString(),
  });
});

// =============================================================================
// EXAMPLE: PUBLIC ENDPOINT (No Auth Required)
// =============================================================================

app.get('/api/public', (req, res) => {
  res.json({
    success: true,
    message: 'This is a public endpoint',
    data: {
      info: 'Anyone can access this',
    },
  });
});

// =============================================================================
// EXAMPLE: PROTECTED ENDPOINT (Auth Required)
// =============================================================================

/**
 * Protected endpoint - requires valid JWT
 * 
 * The authenticate middleware:
 * 1. Extracts JWT from Authorization header
 * 2. Calls Auth Service /api/token/validate
 * 3. Attaches user info to req.user
 * 
 * req.user contains:
 * - id: User ID
 * - email: User email
 * - role: USER, SERVICE_PROVIDER, ADMIN, etc.
 */
app.get('/api/protected', authenticate, (req, res) => {
  res.json({
    success: true,
    message: 'You have access to this protected endpoint',
    user: {
      id: req.user.id,
      email: req.user.email,
      role: req.user.role,
    },
  });
});

// =============================================================================
// EXAMPLE: ROLE-BASED ENDPOINTS
// =============================================================================

/**
 * User-only endpoint
 * Only USER and SERVICE_PROVIDER roles can access
 */
app.get('/api/user/dashboard', 
  authenticate, 
  authorize(['USER', 'SERVICE_PROVIDER']), 
  (req, res) => {
    res.json({
      success: true,
      message: `Welcome to your dashboard, ${req.user.email}`,
      role: req.user.role,
    });
  }
);

/**
 * Admin-only endpoint
 * Only ADMIN, IT_ADMIN, SUPPORT roles can access
 */
app.get('/api/admin/stats', 
  authenticate, 
  authorize(['ADMIN', 'IT_ADMIN', 'SUPPORT']), 
  (req, res) => {
    res.json({
      success: true,
      message: 'Admin statistics',
      admin: {
        id: req.user.id,
        role: req.user.role,
      },
    });
  }
);

// =============================================================================
// EXAMPLE: OPTIONAL AUTH ENDPOINT
// =============================================================================

/**
 * Works for both authenticated and anonymous users
 * 
 * If authenticated: Shows personalized content
 * If anonymous: Shows generic content
 */
app.get('/api/products', optionalAuth, (req, res) => {
  const products = [
    { id: 1, name: 'Plumbing Service', price: 50 },
    { id: 2, name: 'Electrical Work', price: 75 },
  ];
  
  if (req.user) {
    // Personalized response for logged-in users
    res.json({
      success: true,
      user: { id: req.user.id, email: req.user.email },
      message: `Hi ${req.user.email}! Here are your recommended services:`,
      products,
    });
  } else {
    // Generic response for anonymous users
    res.json({
      success: true,
      message: 'Browse our services:',
      products,
    });
  }
});

// =============================================================================
// ERROR HANDLING
// =============================================================================

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    success: false,
    message: 'Endpoint not found',
  });
});

// Global error handler
app.use((err, req, res, next) => {
  console.error('Unhandled error:', err);
  res.status(500).json({
    success: false,
    message: 'Internal server error',
  });
});

// =============================================================================
// START SERVER
// =============================================================================

const PORT = config.server.port;

app.listen(PORT, async () => {
  console.log(`\n🚀 FixHomi Node.js Backend running on port ${PORT}`);
  console.log(`   Environment: ${config.server.env}`);
  console.log(`   Auth Service: ${config.authService.baseUrl}`);
  
  // Check Auth Service health on startup
  const authHealth = await authService.healthCheck();
  if (authHealth.healthy) {
    console.log('   ✅ Auth Service: Connected\n');
  } else {
    console.log('   ⚠️  Auth Service: Not reachable\n');
  }
});

module.exports = app;
```

---

## 📊 Complete API Reference

### API 1: Validate Token

**Endpoint:** `GET /api/token/validate`

**Purpose:** Validate JWT token and get user claims

**Authorization:** `Bearer <access_token>`

**Request:**
```bash
curl -X GET http://localhost:8080/api/token/validate \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

**Response (200 - Valid Token):**
```json
{
  "valid": true,
  "userId": 1,
  "email": "user@example.com",
  "role": "USER",
  "tokenType": "ACCESS",
  "issuedAt": 1734600000,
  "expiresAt": 1734686400
}
```

**Response (200 - Invalid Token):**
```json
{
  "valid": false
}
```

| Field | Type | Description |
|-------|------|-------------|
| `valid` | boolean | Whether token is valid |
| `userId` | number | User's unique ID |
| `email` | string | User's email address |
| `role` | string | USER, SERVICE_PROVIDER, ADMIN, IT_ADMIN, SUPPORT |
| `tokenType` | string | Always "ACCESS" for valid tokens |
| `issuedAt` | number | Unix timestamp when token was issued |
| `expiresAt` | number | Unix timestamp when token expires |

---

### API 2: Get Current User

**Endpoint:** `GET /api/token/me`

**Purpose:** Get user info from JWT (semantic alias for validate)

**Authorization:** `Bearer <access_token>`

**Request:**
```bash
curl -X GET http://localhost:8080/api/token/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

**Response (200):** Same as `/api/token/validate`

---

### API 3: Health Check

**Endpoint:** `GET /api/auth/health`

**Purpose:** Check if Auth Service is running

**Authorization:** None required

**Request:**
```bash
curl -X GET http://localhost:8080/api/auth/health
```

**Response (200):**
```json
{
  "status": "UP",
  "timestamp": "2024-12-19T10:30:00Z"
}
```

---

## 🎭 User Roles Reference

| Role | Description | Access Level |
|------|-------------|--------------|
| `USER` | Regular customer | Can book services |
| `SERVICE_PROVIDER` | Service provider (plumber, etc.) | Can provide services |
| `ADMIN` | Administrator | Full admin access |
| `IT_ADMIN` | IT Administrator | Technical admin |
| `SUPPORT` | Support staff | Customer support |

### Role Usage in Middleware

```javascript
// Any authenticated user
app.get('/api/profile', authenticate, handler);

// Only regular users (customers and providers)
app.get('/api/bookings', authenticate, authorize(['USER', 'SERVICE_PROVIDER']), handler);

// Only service providers
app.get('/api/my-jobs', authenticate, authorize(['SERVICE_PROVIDER']), handler);

// Only admins
app.get('/api/admin/users', authenticate, authorize(['ADMIN', 'IT_ADMIN']), handler);

// Support and above
app.get('/api/support/tickets', authenticate, authorize(['SUPPORT', 'ADMIN', 'IT_ADMIN']), handler);
```

---

## 🔐 Security Best Practices

### 1. Never Store JWT Secret in Node.js

Your Node.js backend does NOT need the JWT secret. It validates tokens by calling the Auth Service API. Only the Java Auth Service knows the secret.

### 2. Always Use HTTPS in Production

```javascript
// .env for production
AUTH_SERVICE_URL=https://auth.fixhomi.com

// NOT this in production:
AUTH_SERVICE_URL=http://auth.fixhomi.com
```

### 3. Handle Auth Service Downtime

```javascript
const validateWithFallback = async (token) => {
  try {
    return await authService.validateToken(token);
  } catch (error) {
    // Log the error
    console.error('Auth Service error:', error);
    
    // Return invalid (fail closed, not open)
    return { valid: false };
  }
};
```

### 4. Set Appropriate Timeouts

```javascript
// Don't let validation hang forever
const authClient = axios.create({
  baseURL: config.authService.baseUrl,
  timeout: 5000, // 5 seconds max
});
```

### 5. Log Authentication Failures

```javascript
if (!validationResult.valid) {
  console.warn('Invalid token attempt:', {
    ip: req.ip,
    userAgent: req.headers['user-agent'],
    timestamp: new Date().toISOString(),
  });
}
```

---

## 🚨 Error Handling Reference

| Scenario | HTTP Status | Response |
|----------|-------------|----------|
| No Authorization header | 401 | `{ "message": "No authorization header provided" }` |
| Invalid header format | 401 | `{ "message": "Invalid authorization header format" }` |
| Invalid/expired token | 401 | `{ "message": "Invalid or expired token" }` |
| Insufficient permissions | 403 | `{ "message": "Access denied. Insufficient permissions." }` |
| Auth Service down | 503 | `{ "message": "Authentication service temporarily unavailable" }` |

---

## 📊 Integration Checklist

### Phase 1: Setup ✅
- [ ] Create project structure
- [ ] Install dependencies
- [ ] Configure environment variables
- [ ] Create env config module

### Phase 2: Auth Service Client ✅
- [ ] Create authService.js
- [ ] Implement validateToken()
- [ ] Implement getCurrentUser()
- [ ] Implement healthCheck()

### Phase 3: Middleware ✅
- [ ] Create authenticate middleware
- [ ] Create authorize middleware
- [ ] Create optionalAuth middleware
- [ ] Test with valid JWT
- [ ] Test with invalid JWT
- [ ] Test with expired JWT

### Phase 4: Application ✅
- [ ] Setup Express app
- [ ] Add health endpoints
- [ ] Add protected routes
- [ ] Add role-based routes
- [ ] Test all endpoints

---

## 🧪 Testing Your Integration

### Step 1: Get a Valid JWT

First, register a user via the Auth Service (you can use Postman or curl):

```bash
# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "TestPass123!",
    "fullName": "Test User",
    "phoneNumber": "+1234567890",
    "role": "USER"
  }'
```

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "test@example.com",
  "fullName": "Test User",
  "role": "USER",
  "expiresIn": 86400
}
```

### Step 2: Test Node.js Protected Endpoint

```bash
# Use the accessToken from registration
curl -X GET http://localhost:3000/api/protected \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

**Expected Response:**
```json
{
  "success": true,
  "message": "You have access to this protected endpoint",
  "user": {
    "id": 1,
    "email": "test@example.com",
    "role": "USER"
  }
}
```

### Step 3: Test Role-Based Access

```bash
# USER trying to access admin endpoint - should fail
curl -X GET http://localhost:3000/api/admin/stats \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

**Expected Response (403):**
```json
{
  "success": false,
  "message": "Access denied. Insufficient permissions."
}
```

### Step 4: Test Invalid Token

```bash
curl -X GET http://localhost:3000/api/protected \
  -H "Authorization: Bearer invalid-token-here"
```

**Expected Response (401):**
```json
{
  "success": false,
  "message": "Invalid or expired token"
}
```

---

## 📅 Estimated Timeline

| Phase | Days | Features |
|-------|------|----------|
| Phase 1 | 1 | Project setup, environment config |
| Phase 2 | 1 | Auth Service client |
| Phase 3 | 1 | Authentication middleware |
| Phase 4 | 1 | Express application, testing |
| **Total** | **4 days** | **Full integration** |

---

## 🎯 Quick Reference

### Headers for Protected APIs
```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json
```

### Environment Variables
| Variable | Default | Description |
|----------|---------|-------------|
| `AUTH_SERVICE_URL` | `http://localhost:8080` | Auth Service base URL |
| `AUTH_SERVICE_TIMEOUT` | `5000` | Request timeout in ms |
| `PORT` | `3000` | Node.js server port |

### Token Info
- Algorithm: **HS512**
- Access Token Expiry: **24 hours**
- Refresh Token Expiry: **7 days**

### User Roles (from Auth Service)
- `USER` - Regular customers
- `SERVICE_PROVIDER` - Service providers
- `ADMIN` - Administrators
- `IT_ADMIN` - IT administrators  
- `SUPPORT` - Support staff

---

**Questions?** Contact FixHomi Engineering Team.
