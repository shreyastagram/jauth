> **Historical (January 2026).** Kept for design/testing history. For the current behaviour of the service see [onboarding/](onboarding/README.md).

# FixHomi Full System Architecture

> **Complete integration guide: Java Auth + Node.js Backend + React Native**  
> **The Synergy of Three Systems**  
> **Last Updated:** January 2026

---

## 🎯 The Critical Question You Asked

> "If Node.js only validates tokens, how will Node.js store user data in MongoDB when the user registers through Java Auth?"

**This is the most important architectural decision.** Let me explain the problem and solution.

---

## 📊 The Problem: Two Databases, One User

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         THE DATA SPLIT PROBLEM                               │
└─────────────────────────────────────────────────────────────────────────────┘

    JAVA AUTH SERVICE                          NODE.JS BACKEND
    (PostgreSQL)                               (MongoDB)
    ┌─────────────────────┐                    ┌─────────────────────┐
    │  users table        │                    │  users collection   │
    │  ─────────────────  │                    │  ─────────────────  │
    │  id: 1              │       ???          │  authUserId: ???    │
    │  email              │  ◄──────────────►  │  addresses: []      │
    │  password_hash      │   How to sync?     │  favorites: []      │
    │  phone              │                    │  bookings: []       │
    │  role               │                    │  reviews: []        │
    │  tokens             │                    │  settings: {}       │
    └─────────────────────┘                    └─────────────────────┘
    
    Auth data only!                            Business data only!
```

### What Each Database Stores

| Java Auth (PostgreSQL) | Node.js (MongoDB) |
|------------------------|-------------------|
| Email | Profile details (bio, avatar) |
| Password hash | Addresses |
| Phone number | Service bookings |
| Role (USER/PROVIDER) | Reviews & ratings |
| Refresh tokens | Favorites |
| Email/Phone verification | Payment methods |
| Login history | Notifications |
| | Provider services |
| | Chat messages |

---

## ✅ The Solution: Coordinated Registration

**The answer is: Node.js should proxy the registration, not just validate tokens.**

### New Architecture: Node.js as the Gateway

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CORRECT ARCHITECTURE: NODE.JS AS GATEWAY                  │
└─────────────────────────────────────────────────────────────────────────────┘

    REACT NATIVE                NODE.JS BACKEND                JAVA AUTH
         │                            │                            │
         │  1. Register Request       │                            │
         │      {email, password,     │                            │
         │       phone, fullName,     │                            │
         │       address, ...}        │                            │
         │───────────────────────────▶│                            │
         │                            │                            │
         │                            │  2. Forward auth fields    │
         │                            │     {email, password,      │
         │                            │      phone, fullName,      │
         │                            │      role}                 │
         │                            │───────────────────────────▶│
         │                            │                            │
         │                            │  3. Returns tokens +       │
         │                            │     userId                 │
         │                            │◀───────────────────────────│
         │                            │                            │
         │                            │  4. Create MongoDB user    │
         │                            │     with authUserId +      │
         │                            │     business fields        │
         │                            │     (address, etc.)        │
         │                            │                            │
         │  5. Return tokens +        │                            │
         │     complete user data     │                            │
         │◀───────────────────────────│                            │
         │                            │                            │
```

---

## 🔄 Complete API Flow: All Three Systems

### Registration Flow (New User)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         REGISTRATION FLOW                                    │
└─────────────────────────────────────────────────────────────────────────────┘

Step 1: React Native → Node.js
────────────────────────────────
POST http://nodejs-backend:3000/api/auth/register
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "phoneNumber": "+1234567890",
  "fullName": "John Doe",
  "role": "USER",
  
  // Business fields (MongoDB only)
  "address": {
    "street": "123 Main St",
    "city": "New York",
    "zipCode": "10001"
  },
  "preferredCategories": ["plumbing", "electrical"]
}


Step 2: Node.js → Java Auth
────────────────────────────────
POST http://java-auth:8080/api/auth/register
{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "phoneNumber": "+1234567890",
  "fullName": "John Doe",
  "role": "USER"
}

Response from Java Auth:
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "userId": 1,              ← THIS IS THE KEY!
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}


Step 3: Node.js Creates MongoDB Document
────────────────────────────────
MongoDB users collection:
{
  "_id": ObjectId("..."),
  "authUserId": 1,           ← Links to Java Auth user.id
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "phoneNumber": "+1234567890",
  
  // Business data (only in MongoDB)
  "address": {
    "street": "123 Main St",
    "city": "New York",
    "zipCode": "10001"
  },
  "preferredCategories": ["plumbing", "electrical"],
  "favorites": [],
  "bookings": [],
  "reviews": [],
  "createdAt": "2026-01-10T10:00:00Z",
  "updatedAt": "2026-01-10T10:00:00Z"
}


Step 4: Node.js → React Native (Final Response)
────────────────────────────────
{
  "success": true,
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "John Doe",
    "role": "USER",
    "phoneNumber": "+1234567890",
    "address": {
      "street": "123 Main St",
      "city": "New York",
      "zipCode": "10001"
    },
    "preferredCategories": ["plumbing", "electrical"]
  }
}
```

---

### Login Flow (Existing User)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            LOGIN FLOW                                        │
└─────────────────────────────────────────────────────────────────────────────┘

Option A: Login through Node.js (Recommended)
─────────────────────────────────────────────

Step 1: React Native → Node.js
POST http://nodejs-backend:3000/api/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Step 2: Node.js → Java Auth
POST http://java-auth:8080/api/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Response:
{
  "accessToken": "eyJ...",
  "refreshToken": "550e...",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}

Step 3: Node.js fetches MongoDB user data
db.users.findOne({ authUserId: 1 })

Step 4: Node.js → React Native
{
  "success": true,
  "accessToken": "eyJ...",
  "refreshToken": "550e...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "fullName": "John Doe",
    "role": "USER",
    "address": { ... },           ← From MongoDB
    "preferredCategories": [...], ← From MongoDB
    "bookingsCount": 5            ← From MongoDB
  }
}


Option B: Login directly to Java Auth (Also Valid)
─────────────────────────────────────────────────

Step 1: React Native → Java Auth (directly)
POST http://java-auth:8080/api/auth/login
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Response: tokens + basic user info

Step 2: React Native → Node.js (get full profile)
GET http://nodejs-backend:3000/api/users/me
Authorization: Bearer eyJ...

Node.js validates token, fetches MongoDB data, returns full profile.
```

---

### Protected API Request Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PROTECTED API REQUEST FLOW                                │
│                  (e.g., Create a Service Booking)                            │
└─────────────────────────────────────────────────────────────────────────────┘

    REACT NATIVE                NODE.JS BACKEND                JAVA AUTH
         │                            │                            │
         │  1. Create Booking         │                            │
         │     POST /api/bookings     │                            │
         │     Authorization: Bearer  │                            │
         │     eyJhbGciOiJIUzUxMiJ9  │                            │
         │     {                      │                            │
         │       "providerId": "...", │                            │
         │       "serviceType":       │                            │
         │         "plumbing",        │                            │
         │       "date": "2026-01-15" │                            │
         │     }                      │                            │
         │───────────────────────────▶│                            │
         │                            │                            │
         │                            │  2. Validate Token         │
         │                            │     GET /api/token/validate│
         │                            │     Authorization: Bearer  │
         │                            │     eyJ...                 │
         │                            │───────────────────────────▶│
         │                            │                            │
         │                            │  3. Token Valid!           │
         │                            │     {                      │
         │                            │       "valid": true,       │
         │                            │       "userId": 1,         │
         │                            │       "email": "...",      │
         │                            │       "role": "USER"       │
         │                            │     }                      │
         │                            │◀───────────────────────────│
         │                            │                            │
         │                            │  4. Get MongoDB user       │
         │                            │     by authUserId: 1       │
         │                            │                            │
         │                            │  5. Create booking in      │
         │                            │     MongoDB with user ref  │
         │                            │                            │
         │  6. Booking Response       │                            │
         │     {                      │                            │
         │       "bookingId": "...",  │                            │
         │       "status": "pending", │                            │
         │       "provider": {...},   │                            │
         │       "scheduledDate": ... │                            │
         │     }                      │                            │
         │◀───────────────────────────│                            │
         │                            │                            │
```

---

## 📋 Complete API List: Who Calls What

### APIs That React Native Calls

| API | Target | Purpose | Auth |
|-----|--------|---------|------|
| `POST /api/auth/register` | **Node.js** | Register (creates both Auth + MongoDB user) | ❌ |
| `POST /api/auth/login` | **Node.js** or Java Auth | Login | ❌ |
| `POST /api/auth/login/phone` | **Node.js** or Java Auth | Login with phone | ❌ |
| `POST /api/auth/logout` | **Java Auth** | Logout (revoke tokens) | ✅ |
| `POST /api/auth/refresh` | **Java Auth** | Refresh tokens | ❌ |
| `POST /api/auth/login/phone/send-otp` | **Java Auth** | Passwordless phone OTP | ❌ |
| `POST /api/auth/login/phone/verify` | **Java Auth** | Verify phone OTP | ❌ |
| `POST /api/auth/login/email/send-otp` | **Java Auth** | Passwordless email OTP | ❌ |
| `POST /api/auth/login/email/verify` | **Java Auth** | Verify email OTP | ❌ |
| `POST /api/auth/forgot-password` | **Java Auth** | Request password reset | ❌ |
| `POST /api/auth/reset-password` | **Java Auth** | Reset password | ❌ |
| `POST /api/auth/otp/send` | **Java Auth** | Phone verification OTP | ✅ |
| `POST /api/auth/otp/verify` | **Java Auth** | Verify phone | ✅ |
| `POST /api/auth/email/send-verification` | **Java Auth** | Email verification | ✅ |
| `GET /api/auth/email/verify` | **Java Auth** | Verify email link | ❌ |
| `POST /api/users/change-password` | **Java Auth** | Change password | ✅ |
| `GET /api/users/me` | **Node.js** | Get full profile (from MongoDB) | ✅ |
| `PUT /api/users/me` | **Node.js** | Update profile | ✅ |
| `POST /api/bookings` | **Node.js** | Create booking | ✅ |
| `GET /api/bookings` | **Node.js** | Get bookings | ✅ |
| `GET /api/providers` | **Node.js** | List providers | ✅/❌ |
| `POST /api/reviews` | **Node.js** | Create review | ✅ |
| ... all business APIs | **Node.js** | Business operations | ✅ |

### APIs That Node.js Calls

| API | Target | Purpose | When |
|-----|--------|---------|------|
| `POST /api/auth/register` | **Java Auth** | Create auth user | User registration |
| `POST /api/auth/login` | **Java Auth** | Authenticate user | User login (if proxied) |
| `GET /api/token/validate` | **Java Auth** | Validate JWT | Every protected request |
| `GET /api/token/me` | **Java Auth** | Get user from JWT | When need user info |
| `GET /api/auth/health` | **Java Auth** | Health check | Startup, monitoring |

---

## 🗂️ Node.js API Structure

### Auth Proxy Routes (Node.js → Java Auth)

```
/api/auth/
├── POST /register      → Proxy to Java Auth + Create MongoDB user
├── POST /login         → Proxy to Java Auth + Return MongoDB profile
├── POST /login/phone   → Proxy to Java Auth + Return MongoDB profile
└── POST /logout        → Proxy to Java Auth (optional)
```

### Business Routes (Node.js MongoDB only)

```
/api/users/
├── GET    /me                → Get full profile from MongoDB
├── PUT    /me                → Update profile in MongoDB
├── GET    /me/addresses      → Get user addresses
├── POST   /me/addresses      → Add address
├── DELETE /me/addresses/:id  → Delete address

/api/bookings/
├── GET    /                  → List user's bookings
├── POST   /                  → Create booking
├── GET    /:id               → Get booking details
├── PUT    /:id               → Update booking
├── DELETE /:id               → Cancel booking

/api/providers/
├── GET    /                  → List providers (with filters)
├── GET    /:id               → Get provider details
├── GET    /:id/services      → Get provider services
├── GET    /:id/reviews       → Get provider reviews
├── GET    /:id/availability  → Get provider availability

/api/services/
├── GET    /categories        → List service categories
├── GET    /                  → List services

/api/reviews/
├── POST   /                  → Create review
├── GET    /provider/:id      → Get reviews for provider

/api/favorites/
├── GET    /                  → Get user's favorites
├── POST   /                  → Add to favorites
├── DELETE /:id               → Remove from favorites

/api/notifications/
├── GET    /                  → Get notifications
├── PUT    /:id/read          → Mark as read

/api/chat/
├── GET    /conversations     → List conversations
├── GET    /conversations/:id → Get messages
├── POST   /conversations/:id → Send message
```

---

## 🔗 The Key Link: authUserId

The **authUserId** field connects Java Auth users to MongoDB documents.

### MongoDB User Schema

```javascript
// MongoDB: users collection
{
  _id: ObjectId("..."),
  
  // ═══════════════════════════════════════════════════════════════════
  // LINK TO JAVA AUTH - This is the critical field!
  // ═══════════════════════════════════════════════════════════════════
  authUserId: 1,  // ← Maps to Java Auth users.id
  
  // Copied from Java Auth (for convenience, but Auth is source of truth)
  email: "user@example.com",
  fullName: "John Doe",
  phoneNumber: "+1234567890",
  role: "USER",  // USER or SERVICE_PROVIDER
  
  // ═══════════════════════════════════════════════════════════════════
  // BUSINESS DATA (Only in MongoDB)
  // ═══════════════════════════════════════════════════════════════════
  avatar: "https://storage.fixhomi.com/avatars/user1.jpg",
  bio: "Homeowner in NYC",
  
  addresses: [
    {
      _id: ObjectId("..."),
      label: "Home",
      street: "123 Main St",
      city: "New York",
      state: "NY",
      zipCode: "10001",
      isDefault: true
    }
  ],
  
  preferredCategories: ["plumbing", "electrical"],
  
  favorites: [
    { providerId: ObjectId("..."), addedAt: Date }
  ],
  
  // For SERVICE_PROVIDER role only
  providerProfile: {
    services: ["plumbing", "pipe-repair"],
    hourlyRate: 75,
    availability: {...},
    serviceArea: ["10001", "10002"],
    certifications: [...],
    verified: true
  },
  
  createdAt: Date,
  updatedAt: Date
}
```

### How Node.js Uses authUserId

```javascript
// In authMiddleware.js - after validating token
const validationResult = await authService.validateToken(token);

if (validationResult.valid) {
  // Get MongoDB user by authUserId
  const mongoUser = await User.findOne({ 
    authUserId: validationResult.userId 
  });
  
  req.user = {
    // From Java Auth
    authUserId: validationResult.userId,
    email: validationResult.email,
    role: validationResult.role,
    
    // From MongoDB
    mongoId: mongoUser?._id,
    addresses: mongoUser?.addresses,
    favorites: mongoUser?.favorites,
    // ... other MongoDB fields
  };
}
```

---

## 🚀 Node.js Implementation: Registration Proxy

### Complete Registration Endpoint

```javascript
// routes/auth.js

const express = require('express');
const router = express.Router();
const axios = require('axios');
const User = require('../models/User');
const config = require('../config/env');

/**
 * POST /api/auth/register
 * 
 * This is the MAIN registration endpoint.
 * React Native calls THIS, not Java Auth directly.
 * 
 * Flow:
 * 1. Receive full registration data
 * 2. Forward auth fields to Java Auth
 * 3. Create MongoDB user with authUserId
 * 4. Return tokens + full user data
 */
router.post('/register', async (req, res) => {
  try {
    const {
      // Auth fields (sent to Java Auth)
      email,
      password,
      phoneNumber,
      fullName,
      role,
      
      // Business fields (MongoDB only)
      address,
      preferredCategories,
      bio,
      // ... other business fields
    } = req.body;
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 1: Register with Java Auth
    // ═══════════════════════════════════════════════════════════════
    let authResponse;
    try {
      authResponse = await axios.post(
        `${config.authService.baseUrl}/api/auth/register`,
        {
          email,
          password,
          phoneNumber,
          fullName,
          role: role || 'USER',
        }
      );
    } catch (authError) {
      // Handle Java Auth errors
      if (authError.response) {
        return res.status(authError.response.status).json({
          success: false,
          message: authError.response.data.message || 'Registration failed',
          errors: authError.response.data.errors,
        });
      }
      throw authError;
    }
    
    const {
      accessToken,
      refreshToken,
      tokenType,
      userId,  // ← This is authUserId!
      expiresIn,
    } = authResponse.data;
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 2: Create MongoDB User
    // ═══════════════════════════════════════════════════════════════
    const mongoUser = new User({
      authUserId: userId,  // ← Link to Java Auth
      email,
      fullName,
      phoneNumber,
      role: role || 'USER',
      
      // Business data
      addresses: address ? [{ ...address, isDefault: true }] : [],
      preferredCategories: preferredCategories || [],
      bio: bio || '',
      favorites: [],
      
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    
    await mongoUser.save();
    
    // ═══════════════════════════════════════════════════════════════
    // STEP 3: Return combined response
    // ═══════════════════════════════════════════════════════════════
    res.status(201).json({
      success: true,
      accessToken,
      refreshToken,
      tokenType,
      expiresIn,
      user: {
        id: userId,
        email,
        fullName,
        phoneNumber,
        role: role || 'USER',
        addresses: mongoUser.addresses,
        preferredCategories: mongoUser.preferredCategories,
        bio: mongoUser.bio,
      },
    });
    
  } catch (error) {
    console.error('Registration error:', error);
    
    // If MongoDB save failed but Auth succeeded, we have a problem
    // In production, consider: retry, queue, or manual cleanup
    
    res.status(500).json({
      success: false,
      message: 'Registration failed. Please try again.',
    });
  }
});

/**
 * POST /api/auth/login
 * 
 * Proxy login through Node.js to get full profile
 */
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    
    // Step 1: Authenticate with Java Auth
    const authResponse = await axios.post(
      `${config.authService.baseUrl}/api/auth/login`,
      { email, password }
    );
    
    const { accessToken, refreshToken, tokenType, userId, expiresIn } = authResponse.data;
    
    // Step 2: Get MongoDB user data
    let mongoUser = await User.findOne({ authUserId: userId });
    
    // If user doesn't exist in MongoDB (edge case), create it
    if (!mongoUser) {
      mongoUser = await User.create({
        authUserId: userId,
        email: authResponse.data.email,
        fullName: authResponse.data.fullName,
        role: authResponse.data.role,
        addresses: [],
        favorites: [],
      });
    }
    
    // Step 3: Return combined response
    res.json({
      success: true,
      accessToken,
      refreshToken,
      tokenType,
      expiresIn,
      user: {
        id: userId,
        email: mongoUser.email,
        fullName: mongoUser.fullName,
        role: mongoUser.role,
        phoneNumber: mongoUser.phoneNumber,
        addresses: mongoUser.addresses,
        preferredCategories: mongoUser.preferredCategories,
        bio: mongoUser.bio,
        avatar: mongoUser.avatar,
      },
    });
    
  } catch (error) {
    if (error.response) {
      return res.status(error.response.status).json({
        success: false,
        message: error.response.data.message || 'Login failed',
      });
    }
    res.status(500).json({
      success: false,
      message: 'Login failed. Please try again.',
    });
  }
});

module.exports = router;
```

---

## 📊 Complete System Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     FIXHOMI COMPLETE SYSTEM ARCHITECTURE                     │
└─────────────────────────────────────────────────────────────────────────────┘


                              REACT NATIVE APP
                    ┌─────────────────────────────────────┐
                    │                                     │
                    │  • Login/Register UI                │
                    │  • Home Screen                      │
                    │  • Booking Flow                     │
                    │  • Provider Listing                 │
                    │  • User Profile                     │
                    │  • Chat                             │
                    │                                     │
                    │  Stores: JWT in Keychain            │
                    │                                     │
                    └──────────────┬──────────────────────┘
                                   │
                                   │ HTTPS
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
          ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────┐
│  Auth-Only APIs │    │  Business APIs      │    │  Direct Auth    │
│                 │    │  (via Node.js)      │    │  (Passwordless, │
│  • Logout       │    │                     │    │   Password      │
│  • Refresh      │    │  • Register *       │    │   Reset)        │
│  • OTP Login    │    │  • Login *          │    │                 │
│                 │    │  • Profile          │    │  /api/auth/     │
│                 │    │  • Bookings         │    │  login/phone/*  │
│                 │    │  • Providers        │    │  login/email/*  │
│                 │    │  • Reviews          │    │  forgot-password│
│                 │    │  • Chat             │    │  reset-password │
│                 │    │  • etc.             │    │                 │
└────────┬────────┘    └──────────┬──────────┘    └────────┬────────┘
         │                        │                        │
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              NODE.JS BACKEND                                 │
│                              (Express.js)                                    │
│  Port: 3000                                                                  │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  Auth Middleware                                                        │ │
│  │  ─────────────────                                                      │ │
│  │  1. Extract JWT from Authorization header                               │ │
│  │  2. Call Java Auth /api/token/validate                                  │ │
│  │  3. Get MongoDB user by authUserId                                      │ │
│  │  4. Attach req.user with combined data                                  │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  Routes:                                                                     │
│  ├── /api/auth/register    → Proxy to Java Auth + Create MongoDB user       │
│  ├── /api/auth/login       → Proxy to Java Auth + Return MongoDB profile    │
│  ├── /api/users/*          → MongoDB user operations                        │
│  ├── /api/bookings/*       → MongoDB booking operations                     │
│  ├── /api/providers/*      → MongoDB provider operations                    │
│  ├── /api/reviews/*        → MongoDB review operations                      │
│  └── /api/chat/*           → MongoDB chat operations                        │
│                                                                              │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │                           │
                    ▼                           ▼
┌─────────────────────────────┐    ┌─────────────────────────────┐
│         JAVA AUTH           │    │         MONGODB              │
│     (Spring Boot 3.4)       │    │                              │
│     Port: 8080              │    │  Collections:                │
│                             │    │  ├── users                   │
│  ┌────────────────────────┐ │    │  ├── bookings                │
│  │  PostgreSQL            │ │    │  ├── services                │
│  │  ────────────────────  │ │    │  ├── reviews                 │
│  │  • users               │ │    │  ├── conversations           │
│  │  • refresh_tokens      │ │    │  ├── messages                │
│  │  • otp_codes           │ │    │  ├── notifications           │
│  │  • email_tokens        │ │    │  └── ...                     │
│  └────────────────────────┘ │    │                              │
│                             │    │  Key Index:                  │
│  APIs:                      │    │  users.authUserId (unique)   │
│  ├── /api/auth/*           │    │                              │
│  ├── /api/token/*          │    └─────────────────────────────┘
│  ├── /api/users/*          │
│  └── /api/admin/*          │
│                             │
│  JWT: HS512, 24h access     │
│  Refresh: 7 days            │
│                             │
└─────────────────────────────┘


* = Proxied through Node.js (recommended) or called directly
```

---

## 🔀 Two Valid Architecture Options

### Option A: Node.js as Full Gateway (Recommended)

React Native **always** talks to Node.js. Node.js proxies auth requests.

```
React Native  →  Node.js  →  Java Auth
                    ↓
                 MongoDB
```

**Pros:**
- Single API endpoint for mobile
- Node.js has full control
- Atomic registration (Auth + MongoDB together)
- Simplified mobile code

**Cons:**
- Extra hop for auth requests
- Node.js must be highly available

### Option B: Hybrid (Direct Auth + Node.js Business)

React Native talks to Java Auth for auth, Node.js for business.

```
React Native  →  Java Auth (for auth)
      ↓
React Native  →  Node.js (for business)
                    ↓
                 MongoDB
```

**Pros:**
- Direct auth (faster login)
- Auth service can be independent

**Cons:**
- Mobile must handle two backends
- User sync can be tricky
- Need "lazy creation" in Node.js

---

## ⚠️ Edge Cases to Handle

### 1. User Exists in Auth but Not MongoDB

Can happen if:
- Registration failed after Auth success
- Database migration issues
- Manual user creation in Auth

**Solution: Lazy Creation**

```javascript
// In authMiddleware.js
const validationResult = await authService.validateToken(token);

if (validationResult.valid) {
  let mongoUser = await User.findOne({ authUserId: validationResult.userId });
  
  // Lazy creation if user doesn't exist
  if (!mongoUser) {
    mongoUser = await User.create({
      authUserId: validationResult.userId,
      email: validationResult.email,
      role: validationResult.role,
      // Minimal data, user can update later
    });
    console.log(`Created missing MongoDB user for authUserId: ${validationResult.userId}`);
  }
  
  req.user = { ...validationResult, mongoUser };
}
```

### 2. Email Changed in Auth but Not MongoDB

**Solution:** Sync on login or use webhook

```javascript
// On login, check and sync email
if (mongoUser.email !== authResponse.data.email) {
  mongoUser.email = authResponse.data.email;
  await mongoUser.save();
}
```

### 3. User Deleted from Auth but Exists in MongoDB

**Solution:** Check on API calls, handle gracefully

```javascript
if (!validationResult.valid) {
  // Token invalid could mean user deleted
  // Optionally: mark MongoDB user as inactive
}
```

---

## 📱 What React Native Needs to Know

### API Base URLs

```javascript
// For Option A (Node.js Gateway)
const API_BASE_URL = 'http://nodejs-backend:3000';

// All requests go to Node.js
POST ${API_BASE_URL}/api/auth/register
POST ${API_BASE_URL}/api/auth/login
GET  ${API_BASE_URL}/api/users/me
POST ${API_BASE_URL}/api/bookings
...

// Except these direct Java Auth calls:
const AUTH_BASE_URL = 'http://java-auth:8080';

POST ${AUTH_BASE_URL}/api/auth/refresh
POST ${AUTH_BASE_URL}/api/auth/logout
POST ${AUTH_BASE_URL}/api/auth/login/phone/send-otp
POST ${AUTH_BASE_URL}/api/auth/login/phone/verify
POST ${AUTH_BASE_URL}/api/auth/forgot-password
...
```

### Request Format

```javascript
// Registration (to Node.js)
POST /api/auth/register
{
  // Auth fields
  "email": "user@example.com",
  "password": "SecurePass123!",
  "phoneNumber": "+1234567890",
  "fullName": "John Doe",
  "role": "USER",
  
  // Business fields (optional)
  "address": {
    "street": "123 Main St",
    "city": "New York",
    "zipCode": "10001"
  }
}

// Response includes everything
{
  "accessToken": "eyJ...",
  "refreshToken": "550e...",
  "user": {
    "id": 1,
    "email": "...",
    "addresses": [...],  // From MongoDB
    ...
  }
}
```

---

## ✅ Summary: The Three-System Synergy

| System | Responsibility | Database |
|--------|----------------|----------|
| **Java Auth** | Authentication, tokens, password, verification | PostgreSQL |
| **Node.js** | Business logic, user profiles, bookings, chat | MongoDB |
| **React Native** | UI, user interaction, API calls | Local storage |

### The Golden Rule

> **authUserId** is the link. Every MongoDB document that belongs to a user has `authUserId` referencing Java Auth's `users.id`.

### Quick Reference

| Action | Who Does It |
|--------|-------------|
| Password validation | Java Auth |
| Token generation | Java Auth |
| Token validation | Java Auth (called by Node.js) |
| OTP generation | Java Auth |
| User profile storage | MongoDB (Node.js) |
| Bookings | MongoDB (Node.js) |
| Reviews | MongoDB (Node.js) |
| Chat | MongoDB (Node.js) |

---

**Questions?** Contact FixHomi Engineering Team.
