# FixHomi Auth Service - React Native Integration Guide

> **Complete integration guide for React Native mobile app developers**  
> **Last Updated:** December 2024  
> **Auth Service Version:** 1.1.0

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Project Setup](#project-setup)
4. [API Configuration](#api-configuration)
5. [Auth Service Module](#auth-service-module)
6. [Secure Token Storage](#secure-token-storage)
7. [API Reference with Examples](#api-reference-with-examples)
8. [Authentication Flows](#authentication-flows)
9. [Context & State Management](#context--state-management)
10. [UI Components](#ui-components)
11. [Google OAuth Integration](#google-oauth-integration)
12. [Error Handling](#error-handling)
13. [Best Practices](#best-practices)
14. [Troubleshooting](#troubleshooting)

---

## Overview

This guide explains how to integrate the FixHomi Auth Service with your React Native application. The Auth Service provides:

- ✅ User registration and login
- ✅ JWT-based authentication (24-hour access tokens)
- ✅ Refresh tokens (7-day expiry with rotation)
- ✅ **Passwordless OTP Login (Phone & Email)** 🆕
- ✅ Email verification
- ✅ Phone OTP verification
- ✅ Password reset
- ✅ Google OAuth2 login
- ✅ Role-based access control

### Architecture Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                        React Native App                          │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │   Screens    │  │   Context    │  │   Secure Storage     │  │
│  │  (Login,     │──│  (AuthContext│──│ (react-native-       │  │
│  │  Register)   │  │   Provider)  │  │  keychain)           │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│         │                 │                     │               │
│         └─────────────────┼─────────────────────┘               │
│                           ▼                                      │
│                  ┌─────────────────┐                            │
│                  │   API Client    │                            │
│                  │   (Axios)       │                            │
│                  └────────┬────────┘                            │
└───────────────────────────┼─────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │   FixHomi Auth Service  │
              │   (Spring Boot)         │
              │   http://api.fixhomi.com│
              └─────────────────────────┘
```

---

## Prerequisites

- React Native 0.70+
- Node.js 18+
- iOS 13+ / Android 8+
- Xcode (for iOS)
- Android Studio (for Android)

---

## Project Setup

### 1. Install Required Dependencies

```bash
# Core dependencies
npm install axios react-native-keychain @react-native-async-storage/async-storage

# For Google OAuth
npm install @react-native-google-signin/google-signin

# For deep linking (OAuth callbacks)
npm install @react-navigation/native @react-navigation/native-stack

# For iOS
cd ios && pod install && cd ..
```

### 2. Project Structure

Create the following folder structure:

```
src/
├── api/
│   ├── client.js           # Axios instance with interceptors
│   └── authService.js      # Auth API calls
├── context/
│   └── AuthContext.js      # Authentication context
├── hooks/
│   └── useAuth.js          # Custom auth hook
├── services/
│   └── tokenService.js     # Secure token storage
├── screens/
│   ├── LoginScreen.js
│   ├── RegisterScreen.js
│   ├── ForgotPasswordScreen.js
│   └── OTPVerificationScreen.js
├── components/
│   └── ProtectedRoute.js   # Route guard component
└── utils/
    └── constants.js        # API URLs, configs
```

---

## API Configuration

### `src/utils/constants.js`

```javascript
// API Configuration
export const API_CONFIG = {
  // Development
  DEV_BASE_URL: 'http://localhost:8080',
  
  // iOS Simulator uses localhost
  // Android Emulator uses 10.0.2.2 for host machine
  LOCAL_BASE_URL: Platform.select({
    ios: 'http://localhost:8080',
    android: 'http://10.0.2.2:8080',
  }),
  
  // Production
  PROD_BASE_URL: 'https://auth.fixhomi.com',
  
  // Current environment
  BASE_URL: __DEV__ 
    ? Platform.select({
        ios: 'http://localhost:8080',
        android: 'http://10.0.2.2:8080',
      })
    : 'https://auth.fixhomi.com',
  
  // Timeouts
  TIMEOUT: 30000,
  
  // Token refresh threshold (5 minutes before expiry)
  TOKEN_REFRESH_THRESHOLD: 5 * 60 * 1000,
};

// API Endpoints
export const ENDPOINTS = {
  // Auth
  REGISTER: '/api/auth/register',
  LOGIN: '/api/auth/login',
  LOGIN_PHONE: '/api/auth/login/phone',  // 🆕 Phone + Password login
  LOGOUT: '/api/auth/logout',
  REFRESH: '/api/auth/refresh',
  HEALTH: '/api/auth/health',
  
  // Token
  VALIDATE_TOKEN: '/api/auth/token/validate',
  TOKEN_ME: '/api/auth/token/me',
  
  // User
  USER_PROFILE: '/api/users/me',
  CHANGE_PASSWORD: '/api/users/me/change-password',
  
  // Verification
  SEND_OTP: '/api/verification/otp/send',
  VERIFY_OTP: '/api/verification/otp/verify',
  SEND_EMAIL_VERIFICATION: '/api/verification/email/send-verification',
  VERIFY_EMAIL: '/api/verification/email/verify',
  
  // Password Reset
  FORGOT_PASSWORD: '/api/verification/forgot-password',
  VALIDATE_RESET_TOKEN: '/api/verification/reset-password/validate',
  RESET_PASSWORD: '/api/verification/reset-password',
  
  // Passwordless OTP Login (NEW)
  PHONE_OTP_LOGIN_SEND: '/api/auth/login/phone/send-otp',
  PHONE_OTP_LOGIN_VERIFY: '/api/auth/login/phone/verify',
  EMAIL_OTP_LOGIN_SEND: '/api/auth/login/email/send-otp',
  EMAIL_OTP_LOGIN_VERIFY: '/api/auth/login/email/verify',
  
  // OAuth
  GOOGLE_AUTH: '/oauth2/authorization/google',
  GOOGLE_MOBILE_AUTH: '/api/auth/oauth2/google/mobile',
};

// User Roles
export const ROLES = {
  USER: 'USER',
  SERVICE_PROVIDER: 'SERVICE_PROVIDER',
  ADMIN: 'ADMIN',
  SUPPORT: 'SUPPORT',
  IT_ADMIN: 'IT_ADMIN',
};

// Error Messages
export const ERROR_MESSAGES = {
  NETWORK_ERROR: 'Unable to connect. Please check your internet connection.',
  SESSION_EXPIRED: 'Your session has expired. Please login again.',
  INVALID_CREDENTIALS: 'Invalid email or password.',
  ACCOUNT_DISABLED: 'Your account has been disabled. Please contact support.',
  EMAIL_EXISTS: 'An account with this email already exists.',
  PHONE_EXISTS: 'An account with this phone number already exists.',
  INVALID_OTP: 'Invalid OTP. Please try again.',
  OTP_EXPIRED: 'OTP has expired. Please request a new one.',
  TOO_MANY_ATTEMPTS: 'Too many attempts. Please try again later.',
  PHONE_NOT_REGISTERED: 'No account found with this phone number.',
  EMAIL_NOT_REGISTERED: 'No account found with this email address.',
};
```

---

## Auth Service Module

### `src/services/tokenService.js`

Secure token storage using Keychain (iOS) and Keystore (Android):

```javascript
import * as Keychain from 'react-native-keychain';
import AsyncStorage from '@react-native-async-storage/async-storage';

const TOKEN_KEYS = {
  ACCESS_TOKEN: 'fixhomi_access_token',
  REFRESH_TOKEN: 'fixhomi_refresh_token',
  USER_DATA: 'fixhomi_user_data',
  TOKEN_EXPIRY: 'fixhomi_token_expiry',
};

/**
 * Token Service - Handles secure storage of authentication tokens
 */
class TokenService {
  /**
   * Store tokens securely
   */
  async storeTokens(accessToken, refreshToken, expiresIn) {
    try {
      // Store access token in Keychain (most secure)
      await Keychain.setGenericPassword(
        TOKEN_KEYS.ACCESS_TOKEN,
        accessToken,
        { service: TOKEN_KEYS.ACCESS_TOKEN }
      );
      
      // Store refresh token in Keychain
      await Keychain.setGenericPassword(
        TOKEN_KEYS.REFRESH_TOKEN,
        refreshToken,
        { service: TOKEN_KEYS.REFRESH_TOKEN }
      );
      
      // Store expiry time
      const expiryTime = Date.now() + (expiresIn * 1000);
      await AsyncStorage.setItem(
        TOKEN_KEYS.TOKEN_EXPIRY,
        expiryTime.toString()
      );
      
      return true;
    } catch (error) {
      console.error('Error storing tokens:', error);
      return false;
    }
  }

  /**
   * Get access token
   */
  async getAccessToken() {
    try {
      const credentials = await Keychain.getGenericPassword({
        service: TOKEN_KEYS.ACCESS_TOKEN
      });
      return credentials ? credentials.password : null;
    } catch (error) {
      console.error('Error getting access token:', error);
      return null;
    }
  }

  /**
   * Get refresh token
   */
  async getRefreshToken() {
    try {
      const credentials = await Keychain.getGenericPassword({
        service: TOKEN_KEYS.REFRESH_TOKEN
      });
      return credentials ? credentials.password : null;
    } catch (error) {
      console.error('Error getting refresh token:', error);
      return null;
    }
  }

  /**
   * Check if token is expired or about to expire
   */
  async isTokenExpired(thresholdMs = 0) {
    try {
      const expiryStr = await AsyncStorage.getItem(TOKEN_KEYS.TOKEN_EXPIRY);
      if (!expiryStr) return true;
      
      const expiryTime = parseInt(expiryStr, 10);
      return Date.now() + thresholdMs >= expiryTime;
    } catch (error) {
      return true;
    }
  }

  /**
   * Store user data
   */
  async storeUserData(userData) {
    try {
      await AsyncStorage.setItem(
        TOKEN_KEYS.USER_DATA,
        JSON.stringify(userData)
      );
      return true;
    } catch (error) {
      console.error('Error storing user data:', error);
      return false;
    }
  }

  /**
   * Get user data
   */
  async getUserData() {
    try {
      const data = await AsyncStorage.getItem(TOKEN_KEYS.USER_DATA);
      return data ? JSON.parse(data) : null;
    } catch (error) {
      console.error('Error getting user data:', error);
      return null;
    }
  }

  /**
   * Clear all tokens (logout)
   */
  async clearTokens() {
    try {
      await Keychain.resetGenericPassword({ service: TOKEN_KEYS.ACCESS_TOKEN });
      await Keychain.resetGenericPassword({ service: TOKEN_KEYS.REFRESH_TOKEN });
      await AsyncStorage.multiRemove([
        TOKEN_KEYS.USER_DATA,
        TOKEN_KEYS.TOKEN_EXPIRY
      ]);
      return true;
    } catch (error) {
      console.error('Error clearing tokens:', error);
      return false;
    }
  }

  /**
   * Check if user is logged in
   */
  async isLoggedIn() {
    const token = await this.getAccessToken();
    return token !== null;
  }
}

export default new TokenService();
```

### `src/api/client.js`

Axios instance with automatic token refresh:

```javascript
import axios from 'axios';
import { API_CONFIG, ENDPOINTS, ERROR_MESSAGES } from '../utils/constants';
import tokenService from '../services/tokenService';

// Create axios instance
const apiClient = axios.create({
  baseURL: API_CONFIG.BASE_URL,
  timeout: API_CONFIG.TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Flag to prevent multiple refresh attempts
let isRefreshing = false;
let refreshSubscribers = [];

// Subscribe to token refresh
const subscribeTokenRefresh = (callback) => {
  refreshSubscribers.push(callback);
};

// Notify all subscribers with new token
const onTokenRefreshed = (token) => {
  refreshSubscribers.forEach(callback => callback(token));
  refreshSubscribers = [];
};

// Request interceptor - Add auth token
apiClient.interceptors.request.use(
  async (config) => {
    // Skip auth header for public endpoints
    const publicEndpoints = [
      ENDPOINTS.LOGIN,
      ENDPOINTS.LOGIN_PHONE,  // 🆕 Phone + Password login
      ENDPOINTS.REGISTER,
      ENDPOINTS.REFRESH,
      ENDPOINTS.FORGOT_PASSWORD,
      ENDPOINTS.RESET_PASSWORD,
      ENDPOINTS.VALIDATE_RESET_TOKEN,
      ENDPOINTS.VERIFY_EMAIL,
      ENDPOINTS.HEALTH,
      // Passwordless OTP Login endpoints (NEW)
      ENDPOINTS.PHONE_OTP_LOGIN_SEND,
      ENDPOINTS.PHONE_OTP_LOGIN_VERIFY,
      ENDPOINTS.EMAIL_OTP_LOGIN_SEND,
      ENDPOINTS.EMAIL_OTP_LOGIN_VERIFY,
    ];
    
    const isPublicEndpoint = publicEndpoints.some(
      endpoint => config.url?.includes(endpoint)
    );
    
    if (!isPublicEndpoint) {
      const token = await tokenService.getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - Handle token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    // Handle network errors
    if (!error.response) {
      return Promise.reject({
        message: ERROR_MESSAGES.NETWORK_ERROR,
        isNetworkError: true,
      });
    }
    
    const { status, data } = error.response;
    
    // Handle 401 - Token expired
    if (status === 401 && !originalRequest._retry) {
      // Check if it's a refresh token request that failed
      if (originalRequest.url?.includes(ENDPOINTS.REFRESH)) {
        // Refresh token is invalid, force logout
        await tokenService.clearTokens();
        return Promise.reject({
          message: ERROR_MESSAGES.SESSION_EXPIRED,
          forceLogout: true,
        });
      }
      
      // Try to refresh the token
      if (!isRefreshing) {
        isRefreshing = true;
        originalRequest._retry = true;
        
        try {
          const refreshToken = await tokenService.getRefreshToken();
          
          if (!refreshToken) {
            throw new Error('No refresh token');
          }
          
          const response = await axios.post(
            `${API_CONFIG.BASE_URL}${ENDPOINTS.REFRESH}`,
            { refreshToken }
          );
          
          const { accessToken, refreshToken: newRefreshToken, expiresIn } = response.data;
          
          await tokenService.storeTokens(accessToken, newRefreshToken, expiresIn);
          
          isRefreshing = false;
          onTokenRefreshed(accessToken);
          
          // Retry original request with new token
          originalRequest.headers.Authorization = `Bearer ${accessToken}`;
          return apiClient(originalRequest);
          
        } catch (refreshError) {
          isRefreshing = false;
          await tokenService.clearTokens();
          return Promise.reject({
            message: ERROR_MESSAGES.SESSION_EXPIRED,
            forceLogout: true,
          });
        }
      }
      
      // Wait for token refresh to complete
      return new Promise((resolve) => {
        subscribeTokenRefresh((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(apiClient(originalRequest));
        });
      });
    }
    
    // Handle other errors
    const errorMessage = data?.message || 'An error occurred';
    return Promise.reject({
      status,
      message: errorMessage,
      validationErrors: data?.validationErrors,
      data,
    });
  }
);

export default apiClient;
```

### `src/api/authService.js`

All Auth API calls:

```javascript
import apiClient from './client';
import { ENDPOINTS } from '../utils/constants';
import tokenService from '../services/tokenService';

/**
 * Auth Service - All authentication related API calls
 */
class AuthService {
  
  // ==================== AUTHENTICATION ====================
  
  /**
   * Register a new user
   * @param {Object} userData - { email, phoneNumber, password, fullName, role }
   * @returns {Promise<Object>} User data with tokens
   */
  async register(userData) {
    const response = await apiClient.post(ENDPOINTS.REGISTER, {
      email: userData.email,
      phoneNumber: userData.phoneNumber || null,
      password: userData.password,
      fullName: userData.fullName,
      role: userData.role || 'USER',
    });
    
    const { accessToken, refreshToken, expiresIn, ...user } = response.data;
    
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken, expiresIn },
    };
  }
  
  /**
   * Login user
   * @param {string} email
   * @param {string} password
   * @returns {Promise<Object>} User data with tokens
   */
  async login(email, password) {
    const response = await apiClient.post(ENDPOINTS.LOGIN, {
      email,
      password,
    });
    
    const { accessToken, refreshToken, expiresIn, ...user } = response.data;
    
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken, expiresIn },
    };
  }
  
  /**
   * Login user with phone number 🆕
   * @param {string} phoneNumber - E.164 format (+1234567890)
   * @param {string} password
   * @returns {Promise<Object>} User data with tokens
   */
  async loginWithPhone(phoneNumber, password) {
    const response = await apiClient.post(ENDPOINTS.LOGIN_PHONE, {
      phoneNumber,
      password,
    });
    
    const { accessToken, refreshToken, expiresIn, ...user } = response.data;
    
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken, expiresIn },
    };
  }
  
  /**
   * Logout user
   * @returns {Promise<void>}
   */
  async logout() {
    try {
      const refreshToken = await tokenService.getRefreshToken();
      if (refreshToken) {
        await apiClient.post(ENDPOINTS.LOGOUT, { refreshToken });
      }
    } catch (error) {
      // Ignore errors during logout
      console.log('Logout API error:', error.message);
    } finally {
      // Always clear local tokens
      await tokenService.clearTokens();
    }
  }
  
  /**
   * Refresh access token
   * @returns {Promise<Object>} New tokens
   */
  async refreshToken() {
    const refreshToken = await tokenService.getRefreshToken();
    
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    
    const response = await apiClient.post(ENDPOINTS.REFRESH, {
      refreshToken,
    });
    
    const { accessToken, refreshToken: newRefreshToken, expiresIn, ...user } = response.data;
    
    await tokenService.storeTokens(accessToken, newRefreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken: newRefreshToken, expiresIn },
    };
  }
  
  /**
   * Check if auth service is healthy
   * @returns {Promise<boolean>}
   */
  async healthCheck() {
    try {
      const response = await apiClient.get(ENDPOINTS.HEALTH);
      return response.data.status === 'UP';
    } catch {
      return false;
    }
  }
  
  // ==================== USER PROFILE ====================
  
  /**
   * Get current user profile
   * @returns {Promise<Object>} User profile
   */
  async getProfile() {
    const response = await apiClient.get(ENDPOINTS.USER_PROFILE);
    await tokenService.storeUserData(response.data);
    return response.data;
  }
  
  /**
   * Get user from token
   * @returns {Promise<Object>} User info from token
   */
  async getUserFromToken() {
    const response = await apiClient.get(ENDPOINTS.TOKEN_ME);
    return response.data;
  }
  
  /**
   * Change password
   * @param {string} currentPassword
   * @param {string} newPassword
   * @returns {Promise<Object>}
   */
  async changePassword(currentPassword, newPassword) {
    const response = await apiClient.post(ENDPOINTS.CHANGE_PASSWORD, {
      currentPassword,
      newPassword,
    });
    return response.data;
  }
  
  // ==================== PHONE VERIFICATION ====================
  
  /**
   * Send OTP to phone number
   * @param {string} phoneNumber
   * @returns {Promise<Object>} { message, expiresInSeconds }
   */
  async sendPhoneOTP(phoneNumber) {
    const response = await apiClient.post(ENDPOINTS.SEND_OTP, {
      phoneNumber,
    });
    return response.data;
  }
  
  /**
   * Verify phone OTP
   * @param {string} phoneNumber
   * @param {string} otp
   * @returns {Promise<Object>} { message, phoneVerified }
   */
  async verifyPhoneOTP(phoneNumber, otp) {
    const response = await apiClient.post(ENDPOINTS.VERIFY_OTP, {
      phoneNumber,
      otp,
    });
    return response.data;
  }
  
  // ==================== EMAIL VERIFICATION ====================
  
  /**
   * Send email verification
   * @returns {Promise<Object>} { message, expiresInHours }
   */
  async sendEmailVerification() {
    const response = await apiClient.post(ENDPOINTS.SEND_EMAIL_VERIFICATION);
    return response.data;
  }
  
  /**
   * Verify email token
   * @param {string} token
   * @returns {Promise<Object>} { message, emailVerified }
   */
  async verifyEmail(token) {
    const response = await apiClient.get(
      `${ENDPOINTS.VERIFY_EMAIL}?token=${token}`
    );
    return response.data;
  }
  
  // ==================== PASSWORD RESET ====================
  
  /**
   * Request password reset
   * @param {string} email
   * @returns {Promise<Object>} { message }
   */
  async forgotPassword(email) {
    const response = await apiClient.post(ENDPOINTS.FORGOT_PASSWORD, {
      email,
    });
    return response.data;
  }
  
  /**
   * Validate reset token
   * @param {string} token
   * @returns {Promise<Object>} { valid, email }
   */
  async validateResetToken(token) {
    const response = await apiClient.get(
      `${ENDPOINTS.VALIDATE_RESET_TOKEN}?token=${token}`
    );
    return response.data;
  }
  
  /**
   * Reset password
   * @param {string} token
   * @param {string} newPassword
   * @returns {Promise<Object>} { message }
   */
  async resetPassword(token, newPassword) {
    const response = await apiClient.post(ENDPOINTS.RESET_PASSWORD, {
      token,
      newPassword,
    });
    return response.data;
  }
  
  // ==================== TOKEN VALIDATION ====================
  
  /**
   * Validate current token
   * @returns {Promise<Object>} { valid, userId, email, role, expiresAt }
   */
  async validateToken() {
    const response = await apiClient.get(ENDPOINTS.VALIDATE_TOKEN);
    return response.data;
  }
  
  // ==================== PASSWORDLESS OTP LOGIN (NEW) ====================
  
  /**
   * Send OTP to phone number for passwordless login
   * @param {string} phoneNumber - Phone number with country code
   * @returns {Promise<Object>} { success, message, maskedPhone, expiresInMinutes }
   */
  async sendPhoneLoginOtp(phoneNumber) {
    const response = await apiClient.post(ENDPOINTS.PHONE_OTP_LOGIN_SEND, {
      phoneNumber,
    });
    return response.data;
  }
  
  /**
   * Verify phone OTP and complete passwordless login
   * @param {string} phoneNumber - Phone number with country code
   * @param {string} otp - 6-digit OTP code
   * @returns {Promise<Object>} User data with tokens (same as regular login)
   */
  async verifyPhoneLoginOtp(phoneNumber, otp) {
    const response = await apiClient.post(ENDPOINTS.PHONE_OTP_LOGIN_VERIFY, {
      phoneNumber,
      otp,
    });
    
    const { accessToken, refreshToken, expiresIn, ...user } = response.data;
    
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken, expiresIn },
    };
  }
  
  /**
   * Send OTP to email for passwordless login
   * @param {string} email - User's email address
   * @returns {Promise<Object>} { success, message, maskedEmail, expiresInMinutes }
   */
  async sendEmailLoginOtp(email) {
    const response = await apiClient.post(ENDPOINTS.EMAIL_OTP_LOGIN_SEND, {
      email,
    });
    return response.data;
  }
  
  /**
   * Verify email OTP and complete passwordless login
   * @param {string} email - User's email address
   * @param {string} otp - 6-digit OTP code
   * @returns {Promise<Object>} User data with tokens (same as regular login)
   */
  async verifyEmailLoginOtp(email, otp) {
    const response = await apiClient.post(ENDPOINTS.EMAIL_OTP_LOGIN_VERIFY, {
      email,
      otp,
    });
    
    const { accessToken, refreshToken, expiresIn, ...user } = response.data;
    
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, expiresIn);
    await tokenService.storeUserData(user);
    
    return {
      user,
      tokens: { accessToken, refreshToken, expiresIn },
    };
  }
  
  /**
   * Check if user is authenticated (local check)
   * @returns {Promise<boolean>}
   */
  async isAuthenticated() {
    const token = await tokenService.getAccessToken();
    if (!token) return false;
    
    // Check if token is expired locally
    const isExpired = await tokenService.isTokenExpired();
    if (!isExpired) return true;
    
    // Try to refresh if expired
    try {
      await this.refreshToken();
      return true;
    } catch {
      return false;
    }
  }
  
  /**
   * Get stored user data (local)
   * @returns {Promise<Object|null>}
   */
  async getStoredUser() {
    return await tokenService.getUserData();
  }
}

export default new AuthService();
```

---

## API Reference with Examples

### 1. Register User

**Endpoint:** `POST /api/auth/register`

**Allowed Roles for Public Registration:** `USER`, `SERVICE_PROVIDER`

```javascript
// Example usage
import authService from '../api/authService';

const handleRegister = async () => {
  try {
    const result = await authService.register({
      email: 'john@example.com',
      phoneNumber: '+1234567890',
      password: 'SecurePass123!',
      fullName: 'John Doe',
      role: 'USER',
    });
    
    console.log('User registered:', result.user);
    console.log('Access token:', result.tokens.accessToken);
    
    // Navigate to home or verification screen
    navigation.replace('Home');
    
  } catch (error) {
    if (error.status === 409) {
      Alert.alert('Error', 'Email or phone already registered');
    } else if (error.validationErrors) {
      // Handle validation errors
      const errors = Object.values(error.validationErrors).join('\n');
      Alert.alert('Validation Error', errors);
    } else {
      Alert.alert('Error', error.message);
    }
  }
};
```

**Password Requirements:**
- Minimum 8 characters
- At least 1 uppercase letter
- At least 1 lowercase letter
- At least 1 digit
- At least 1 special character (!@#$%^&*)

---

### 2. Login

**Endpoint:** `POST /api/auth/login`

```javascript
const handleLogin = async (email, password) => {
  try {
    setLoading(true);
    
    const result = await authService.login(email, password);
    
    console.log('Logged in as:', result.user.fullName);
    console.log('Role:', result.user.role);
    
    // Navigate based on role
    if (result.user.role === 'SERVICE_PROVIDER') {
      navigation.replace('ProviderHome');
    } else {
      navigation.replace('UserHome');
    }
    
  } catch (error) {
    if (error.message.includes('disabled')) {
      Alert.alert(
        'Account Disabled',
        'Your account has been disabled. Please contact support.',
        [{ text: 'OK' }]
      );
    } else if (error.status === 401) {
      Alert.alert('Login Failed', 'Invalid email or password');
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 2b. Login with Phone Number 🆕

**Endpoint:** `POST /api/auth/login/phone`

Users can also login using their phone number and password (alternative to email login).

```javascript
const handlePhoneLogin = async (phoneNumber, password) => {
  try {
    setLoading(true);
    
    const response = await apiClient.post('/api/auth/login/phone', {
      phoneNumber: phoneNumber,  // E.164 format: +1234567890
      password: password
    });
    
    // Store tokens securely
    await tokenService.saveTokens(response.data.accessToken, response.data.refreshToken);
    
    console.log('Logged in as:', response.data.user.fullName);
    console.log('Role:', response.data.user.role);
    
    // Navigate based on role
    if (response.data.user.role === 'SERVICE_PROVIDER') {
      navigation.replace('ProviderHome');
    } else {
      navigation.replace('UserHome');
    }
    
  } catch (error) {
    if (error.response?.status === 401) {
      Alert.alert('Login Failed', 'Invalid phone number or password');
    } else if (error.response?.status === 404) {
      Alert.alert('Login Failed', 'No account found with this phone number');
    } else {
      Alert.alert('Error', error.response?.data?.message || 'Login failed');
    }
  } finally {
    setLoading(false);
  }
};
```

**Request Format:**
```json
{
  "phoneNumber": "+1234567890",
  "password": "YourPassword123!"
}
```

**Response:** Same as email login (LoginResponse with tokens and user info)

---

### 3. Logout

**Endpoint:** `POST /api/auth/logout`

```javascript
const handleLogout = async () => {
  Alert.alert(
    'Logout',
    'Are you sure you want to logout?',
    [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Logout',
        style: 'destructive',
        onPress: async () => {
          try {
            await authService.logout();
            navigation.reset({
              index: 0,
              routes: [{ name: 'Login' }],
            });
          } catch (error) {
            // Still logout locally even if API fails
            navigation.reset({
              index: 0,
              routes: [{ name: 'Login' }],
            });
          }
        },
      },
    ]
  );
};
```

---

### 4. Refresh Token

**Endpoint:** `POST /api/auth/refresh`

```javascript
// Automatic refresh is handled by the API client interceptor
// Manual refresh example:

const manualRefresh = async () => {
  try {
    const result = await authService.refreshToken();
    console.log('Token refreshed');
    console.log('New expiry:', result.tokens.expiresIn, 'seconds');
  } catch (error) {
    // Force logout if refresh fails
    await authService.logout();
    navigation.reset({
      index: 0,
      routes: [{ name: 'Login' }],
    });
  }
};
```

---

### 5. Send Phone OTP

**Endpoint:** `POST /api/verification/otp/send`

```javascript
const handleSendOTP = async (phoneNumber) => {
  try {
    setLoading(true);
    
    const result = await authService.sendPhoneOTP(phoneNumber);
    
    Alert.alert(
      'OTP Sent',
      `OTP sent to ${phoneNumber}. Valid for ${result.expiresInSeconds / 60} minutes.`
    );
    
    // Navigate to OTP input screen
    navigation.navigate('OTPVerification', { phoneNumber });
    
  } catch (error) {
    if (error.status === 429) {
      Alert.alert(
        'Too Many Requests',
        'Please wait before requesting another OTP.'
      );
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 6. Verify Phone OTP

**Endpoint:** `POST /api/verification/otp/verify`

```javascript
const handleVerifyOTP = async (phoneNumber, otp) => {
  try {
    setLoading(true);
    
    const result = await authService.verifyPhoneOTP(phoneNumber, otp);
    
    if (result.phoneVerified) {
      Alert.alert('Success', 'Phone number verified!');
      navigation.goBack();
    }
    
  } catch (error) {
    if (error.message.includes('expired')) {
      Alert.alert('OTP Expired', 'Please request a new OTP.');
    } else if (error.status === 429) {
      Alert.alert(
        'Too Many Attempts',
        'Maximum attempts exceeded. Please request a new OTP.'
      );
    } else {
      Alert.alert('Invalid OTP', 'Please check the OTP and try again.');
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 7. Send Email Verification

**Endpoint:** `POST /api/verification/email/send-verification`

```javascript
const handleSendEmailVerification = async () => {
  try {
    setLoading(true);
    
    const result = await authService.sendEmailVerification();
    
    Alert.alert(
      'Email Sent',
      `Verification email sent. Valid for ${result.expiresInHours} hours.`
    );
    
  } catch (error) {
    Alert.alert('Error', error.message);
  } finally {
    setLoading(false);
  }
};
```

---

### 8. Verify Email Token

**Endpoint:** `GET /api/verification/email/verify?token=<token>`

Handle deep links for email verification:

```javascript
// In your deep link handler
import { Linking } from 'react-native';
import authService from '../api/authService';

const handleDeepLink = async (url) => {
  // Parse URL: fixhomi://verify-email?token=abc123
  const { path, queryParams } = parseUrl(url);
  
  if (path === 'verify-email' && queryParams.token) {
    try {
      const result = await authService.verifyEmail(queryParams.token);
      
      if (result.emailVerified) {
        Alert.alert('Success', 'Email verified successfully!');
      }
    } catch (error) {
      Alert.alert('Verification Failed', error.message);
    }
  }
};

// Setup deep link listener
useEffect(() => {
  const subscription = Linking.addEventListener('url', ({ url }) => {
    handleDeepLink(url);
  });
  
  return () => subscription.remove();
}, []);
```

---

### 9. Forgot Password

**Endpoint:** `POST /api/verification/forgot-password`

```javascript
const handleForgotPassword = async (email) => {
  try {
    setLoading(true);
    
    await authService.forgotPassword(email);
    
    // Always show success to prevent email enumeration
    Alert.alert(
      'Email Sent',
      'If an account exists with this email, you will receive a password reset link.',
      [
        {
          text: 'OK',
          onPress: () => navigation.goBack(),
        },
      ]
    );
    
  } catch (error) {
    // Still show success message
    Alert.alert(
      'Email Sent',
      'If an account exists with this email, you will receive a password reset link.'
    );
  } finally {
    setLoading(false);
  }
};
```

---

### 10. Reset Password

**Endpoint:** `POST /api/verification/reset-password`

Handle deep links for password reset:

```javascript
// Password Reset Screen
const ResetPasswordScreen = ({ route }) => {
  const { token } = route.params;
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [tokenValid, setTokenValid] = useState(null);
  
  // Validate token on mount
  useEffect(() => {
    validateToken();
  }, []);
  
  const validateToken = async () => {
    try {
      const result = await authService.validateResetToken(token);
      setTokenValid(result.valid);
    } catch {
      setTokenValid(false);
    }
  };
  
  const handleResetPassword = async () => {
    if (password !== confirmPassword) {
      Alert.alert('Error', 'Passwords do not match');
      return;
    }
    
    try {
      setLoading(true);
      
      await authService.resetPassword(token, password);
      
      Alert.alert(
        'Success',
        'Password reset successfully. Please login with your new password.',
        [
          {
            text: 'Login',
            onPress: () => navigation.replace('Login'),
          },
        ]
      );
      
    } catch (error) {
      Alert.alert('Error', error.message);
    } finally {
      setLoading(false);
    }
  };
  
  if (tokenValid === false) {
    return (
      <View style={styles.container}>
        <Text>This reset link has expired or is invalid.</Text>
        <Button
          title="Request New Link"
          onPress={() => navigation.replace('ForgotPassword')}
        />
      </View>
    );
  }
  
  return (
    // Password reset form
  );
};
```

---

### 11. Change Password

**Endpoint:** `POST /api/users/me/change-password`

```javascript
const handleChangePassword = async (currentPassword, newPassword) => {
  try {
    setLoading(true);
    
    await authService.changePassword(currentPassword, newPassword);
    
    Alert.alert('Success', 'Password changed successfully');
    navigation.goBack();
    
  } catch (error) {
    if (error.status === 401) {
      Alert.alert('Error', 'Current password is incorrect');
    } else if (error.validationErrors) {
      Alert.alert('Error', 'New password does not meet requirements');
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 12. Get User Profile

**Endpoint:** `GET /api/users/me`

```javascript
const fetchProfile = async () => {
  try {
    setLoading(true);
    
    const profile = await authService.getProfile();
    
    setUser({
      id: profile.id,
      email: profile.email,
      phone: profile.phoneNumber,
      name: profile.fullName,
      role: profile.role,
      emailVerified: profile.emailVerified,
      phoneVerified: profile.phoneVerified,
      createdAt: profile.createdAt,
      lastLogin: profile.lastLoginAt,
    });
    
  } catch (error) {
    if (error.forceLogout) {
      // Session expired, redirect to login
      navigation.reset({
        index: 0,
        routes: [{ name: 'Login' }],
      });
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 13. Passwordless Phone OTP Login 🆕

**Step 1: Send OTP**

**Endpoint:** `POST /api/auth/login/phone/send-otp`

```javascript
const handleSendPhoneLoginOtp = async (phoneNumber) => {
  try {
    setLoading(true);
    
    const result = await authService.sendPhoneLoginOtp(phoneNumber);
    
    Alert.alert(
      'OTP Sent',
      `Code sent to ${result.maskedPhone}. Valid for ${result.expiresInMinutes} minutes.`
    );
    
    // Navigate to OTP verification screen
    navigation.navigate('PhoneOtpVerify', { phoneNumber });
    
  } catch (error) {
    if (error.status === 404) {
      Alert.alert('Not Found', 'No account found with this phone number.');
    } else if (error.status === 429) {
      Alert.alert('Too Many Requests', 'Please wait before requesting another OTP.');
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

**Step 2: Verify OTP and Login**

**Endpoint:** `POST /api/auth/login/phone/verify`

```javascript
const handleVerifyPhoneLoginOtp = async (phoneNumber, otp) => {
  try {
    setLoading(true);
    
    // Using AuthContext for automatic state update
    const { loginWithPhoneOtp } = useAuth();
    const result = await loginWithPhoneOtp(phoneNumber, otp);
    
    console.log('Logged in as:', result.user.fullName);
    // Navigation is handled automatically by AuthContext
    
  } catch (error) {
    if (error.message.includes('expired')) {
      Alert.alert('OTP Expired', 'Please request a new OTP.');
    } else if (error.status === 429) {
      Alert.alert('Too Many Attempts', 'Maximum attempts exceeded. Please request a new OTP.');
    } else {
      Alert.alert('Invalid OTP', 'Please check the code and try again.');
    }
  } finally {
    setLoading(false);
  }
};
```

---

### 14. Passwordless Email OTP Login 🆕

**Step 1: Send OTP**

**Endpoint:** `POST /api/auth/login/email/send-otp`

```javascript
const handleSendEmailLoginOtp = async (email) => {
  try {
    setLoading(true);
    
    const result = await authService.sendEmailLoginOtp(email);
    
    Alert.alert(
      'OTP Sent',
      `Code sent to ${result.maskedEmail}. Valid for ${result.expiresInMinutes} minutes.`
    );
    
    // Navigate to OTP verification screen
    navigation.navigate('EmailOtpVerify', { email });
    
  } catch (error) {
    if (error.status === 404) {
      Alert.alert('Not Found', 'No account found with this email address.');
    } else if (error.status === 429) {
      Alert.alert('Too Many Requests', 'Please wait before requesting another OTP.');
    } else {
      Alert.alert('Error', error.message);
    }
  } finally {
    setLoading(false);
  }
};
```

**Step 2: Verify OTP and Login**

**Endpoint:** `POST /api/auth/login/email/verify`

```javascript
const handleVerifyEmailLoginOtp = async (email, otp) => {
  try {
    setLoading(true);
    
    // Using AuthContext for automatic state update
    const { loginWithEmailOtp } = useAuth();
    const result = await loginWithEmailOtp(email, otp);
    
    console.log('Logged in as:', result.user.fullName);
    // Navigation is handled automatically by AuthContext
    
  } catch (error) {
    if (error.message.includes('expired')) {
      Alert.alert('OTP Expired', 'Please request a new OTP.');
    } else if (error.status === 429) {
      Alert.alert('Too Many Attempts', 'Maximum attempts exceeded. Please request a new OTP.');
    } else {
      Alert.alert('Invalid OTP', 'Please check the code and try again.');
    }
  } finally {
    setLoading(false);
  }
};
```

---

## Context & State Management

### `src/context/AuthContext.js`

```javascript
import React, { createContext, useState, useEffect, useContext } from 'react';
import authService from '../api/authService';
import tokenService from '../services/tokenService';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  // Check auth state on mount
  useEffect(() => {
    checkAuthState();
  }, []);

  const checkAuthState = async () => {
    try {
      setLoading(true);
      
      const isAuth = await authService.isAuthenticated();
      
      if (isAuth) {
        const userData = await tokenService.getUserData();
        setUser(userData);
        setIsAuthenticated(true);
      } else {
        setUser(null);
        setIsAuthenticated(false);
      }
    } catch (error) {
      console.error('Auth check failed:', error);
      setUser(null);
      setIsAuthenticated(false);
    } finally {
      setLoading(false);
    }
  };

  const login = async (email, password) => {
    const result = await authService.login(email, password);
    setUser(result.user);
    setIsAuthenticated(true);
    return result;
  };

  const register = async (userData) => {
    const result = await authService.register(userData);
    setUser(result.user);
    setIsAuthenticated(true);
    return result;
  };

  const logout = async () => {
    await authService.logout();
    setUser(null);
    setIsAuthenticated(false);
  };

  const refreshUser = async () => {
    const profile = await authService.getProfile();
    setUser(profile);
    return profile;
  };

  // Passwordless OTP Login methods (NEW)
  const loginWithPhoneOtp = async (phoneNumber, otp) => {
    const result = await authService.verifyPhoneLoginOtp(phoneNumber, otp);
    setUser(result.user);
    setIsAuthenticated(true);
    return result;
  };

  const loginWithEmailOtp = async (email, otp) => {
    const result = await authService.verifyEmailLoginOtp(email, otp);
    setUser(result.user);
    setIsAuthenticated(true);
    return result;
  };

  const value = {
    user,
    loading,
    isAuthenticated,
    login,
    register,
    logout,
    refreshUser,
    checkAuthState,
    // OTP Login methods (NEW)
    loginWithPhoneOtp,
    loginWithEmailOtp,
    sendPhoneLoginOtp: authService.sendPhoneLoginOtp.bind(authService),
    sendEmailLoginOtp: authService.sendEmailLoginOtp.bind(authService),
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export default AuthContext;
```

### Usage in App.js

```javascript
import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { AuthProvider, useAuth } from './src/context/AuthContext';
import AuthNavigator from './src/navigation/AuthNavigator';
import MainNavigator from './src/navigation/MainNavigator';
import LoadingScreen from './src/screens/LoadingScreen';

const AppContent = () => {
  const { isAuthenticated, loading } = useAuth();
  
  if (loading) {
    return <LoadingScreen />;
  }
  
  return (
    <NavigationContainer>
      {isAuthenticated ? <MainNavigator /> : <AuthNavigator />}
    </NavigationContainer>
  );
};

const App = () => {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
};

export default App;
```

---

## UI Components

### Login Screen Example

```javascript
import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { useAuth } from '../context/AuthContext';

const LoginScreen = ({ navigation }) => {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState({});

  const validate = () => {
    const newErrors = {};
    
    if (!email) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(email)) {
      newErrors.email = 'Invalid email format';
    }
    
    if (!password) {
      newErrors.password = 'Password is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleLogin = async () => {
    if (!validate()) return;
    
    try {
      setLoading(true);
      await login(email, password);
      // Navigation is handled by AuthContext
    } catch (error) {
      if (error.message.includes('disabled')) {
        Alert.alert('Account Disabled', 'Please contact support.');
      } else {
        Alert.alert('Login Failed', 'Invalid email or password');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.container}
    >
      <View style={styles.form}>
        <Text style={styles.title}>Welcome Back</Text>
        <Text style={styles.subtitle}>Login to your account</Text>
        
        <View style={styles.inputContainer}>
          <Text style={styles.label}>Email</Text>
          <TextInput
            style={[styles.input, errors.email && styles.inputError]}
            value={email}
            onChangeText={setEmail}
            placeholder="Enter your email"
            keyboardType="email-address"
            autoCapitalize="none"
            autoCorrect={false}
          />
          {errors.email && (
            <Text style={styles.errorText}>{errors.email}</Text>
          )}
        </View>
        
        <View style={styles.inputContainer}>
          <Text style={styles.label}>Password</Text>
          <TextInput
            style={[styles.input, errors.password && styles.inputError]}
            value={password}
            onChangeText={setPassword}
            placeholder="Enter your password"
            secureTextEntry
          />
          {errors.password && (
            <Text style={styles.errorText}>{errors.password}</Text>
          )}
        </View>
        
        <TouchableOpacity
          onPress={() => navigation.navigate('ForgotPassword')}
        >
          <Text style={styles.forgotPassword}>Forgot Password?</Text>
        </TouchableOpacity>
        
        <TouchableOpacity
          style={[styles.button, loading && styles.buttonDisabled]}
          onPress={handleLogin}
          disabled={loading}
        >
          <Text style={styles.buttonText}>
            {loading ? 'Logging in...' : 'Login'}
          </Text>
        </TouchableOpacity>
        
        <View style={styles.registerContainer}>
          <Text style={styles.registerText}>Don't have an account? </Text>
          <TouchableOpacity onPress={() => navigation.navigate('Register')}>
            <Text style={styles.registerLink}>Sign Up</Text>
          </TouchableOpacity>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  form: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
    marginBottom: 32,
  },
  inputContainer: {
    marginBottom: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
  },
  inputError: {
    borderColor: '#ff4444',
  },
  errorText: {
    color: '#ff4444',
    fontSize: 12,
    marginTop: 4,
  },
  forgotPassword: {
    color: '#007AFF',
    textAlign: 'right',
    marginBottom: 24,
  },
  button: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonDisabled: {
    opacity: 0.7,
  },
  buttonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  registerContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 24,
  },
  registerText: {
    color: '#666',
  },
  registerLink: {
    color: '#007AFF',
    fontWeight: '600',
  },
});

export default LoginScreen;
```

---

## Google OAuth Integration

### Setup

1. **Install package:**
```bash
npm install @react-native-google-signin/google-signin
```

2. **Configure in Google Cloud Console:**
   - Create OAuth 2.0 credentials
   - Add iOS and Android app credentials

### `src/services/googleAuth.js`

```javascript
import {
  GoogleSignin,
  statusCodes,
} from '@react-native-google-signin/google-signin';
import { API_CONFIG, ENDPOINTS } from '../utils/constants';
import tokenService from './tokenService';
import { Linking } from 'react-native';

// Initialize Google Sign In
export const configureGoogleSignIn = () => {
  GoogleSignin.configure({
    webClientId: 'YOUR_WEB_CLIENT_ID.apps.googleusercontent.com',
    offlineAccess: true,
    forceCodeForRefreshToken: true,
  });
};

/**
 * Sign in with Google using OAuth2 flow
 */
export const signInWithGoogle = async () => {
  try {
    await GoogleSignin.hasPlayServices();
    
    // This will open the Auth Service's OAuth endpoint
    const authUrl = `${API_CONFIG.BASE_URL}${ENDPOINTS.GOOGLE_AUTH}`;
    
    // Open in browser for OAuth flow
    const canOpen = await Linking.canOpenURL(authUrl);
    if (canOpen) {
      await Linking.openURL(authUrl);
    } else {
      throw new Error('Cannot open Google Sign In');
    }
    
    // The Auth Service will redirect back with tokens
    // Handle this in your deep link handler
    
  } catch (error) {
    if (error.code === statusCodes.SIGN_IN_CANCELLED) {
      throw new Error('Sign in cancelled');
    } else if (error.code === statusCodes.IN_PROGRESS) {
      throw new Error('Sign in already in progress');
    } else if (error.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
      throw new Error('Play services not available');
    } else {
      throw error;
    }
  }
};

/**
 * Handle OAuth callback from deep link
 */
export const handleOAuthCallback = async (url) => {
  // URL format: fixhomi://oauth-callback?token=<jwt>&refresh=<refresh_token>
  const params = new URLSearchParams(url.split('?')[1]);
  
  const accessToken = params.get('token');
  const refreshToken = params.get('refresh');
  const error = params.get('error');
  
  if (error) {
    throw new Error(error);
  }
  
  if (accessToken && refreshToken) {
    // Store tokens
    await tokenService.storeTokens(accessToken, refreshToken, 86400);
    return true;
  }
  
  throw new Error('Invalid OAuth callback');
};
```

---

## Error Handling

### Error Types

| Status Code | Error Type | User Action |
|-------------|------------|-------------|
| 400 | Validation Error | Show field errors |
| 401 | Authentication Failed | Show error, retry login |
| 401 + forceLogout | Session Expired | Redirect to login |
| 403 | Forbidden | Show access denied |
| 404 | Not Found | Show not found message |
| 409 | Conflict | Email/phone exists |
| 429 | Too Many Requests | Show rate limit message |
| 500 | Server Error | Show generic error |
| Network Error | No internet | Show connection error |

### Global Error Handler

```javascript
// src/utils/errorHandler.js
import { Alert } from 'react-native';
import { ERROR_MESSAGES } from './constants';

export const handleAuthError = (error, navigation) => {
  // Force logout
  if (error.forceLogout) {
    Alert.alert(
      'Session Expired',
      ERROR_MESSAGES.SESSION_EXPIRED,
      [
        {
          text: 'Login',
          onPress: () => {
            navigation.reset({
              index: 0,
              routes: [{ name: 'Login' }],
            });
          },
        },
      ],
      { cancelable: false }
    );
    return;
  }
  
  // Network error
  if (error.isNetworkError) {
    Alert.alert('Connection Error', ERROR_MESSAGES.NETWORK_ERROR);
    return;
  }
  
  // Validation errors
  if (error.validationErrors) {
    const messages = Object.values(error.validationErrors).join('\n');
    Alert.alert('Validation Error', messages);
    return;
  }
  
  // Rate limiting
  if (error.status === 429) {
    Alert.alert('Too Many Requests', ERROR_MESSAGES.TOO_MANY_ATTEMPTS);
    return;
  }
  
  // Default error
  Alert.alert('Error', error.message || 'An unexpected error occurred');
};
```

---

## Best Practices

### 1. Secure Storage
- ✅ Use `react-native-keychain` for tokens (uses iOS Keychain / Android Keystore)
- ❌ Never use `AsyncStorage` for sensitive data
- ❌ Never store tokens in Redux state that persists

### 2. Token Management
- ✅ Implement automatic token refresh
- ✅ Clear tokens on logout
- ✅ Handle token expiry gracefully

### 3. Error Handling
- ✅ Show user-friendly error messages
- ✅ Handle network errors gracefully
- ✅ Implement retry logic for failed requests

### 4. Security
- ✅ Use HTTPS in production
- ✅ Implement certificate pinning for sensitive apps
- ✅ Validate all user inputs
- ❌ Never log sensitive data (passwords, tokens)

### 5. UX
- ✅ Show loading states during API calls
- ✅ Disable buttons while loading
- ✅ Provide clear feedback for all actions

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Network Error" on Android | HTTP not allowed | Use HTTPS or add `android:usesCleartextTraffic="true"` |
| Token not persisting | Keychain not configured | Check `react-native-keychain` setup |
| 401 on every request | Token not sent | Check interceptor is adding Authorization header |
| Refresh loop | Refresh token also expired | Force logout user |
| Google Sign In fails | Missing configuration | Check Google Cloud Console setup |

### Debug Tips

```javascript
// Log API requests (development only)
if (__DEV__) {
  apiClient.interceptors.request.use(request => {
    console.log('API Request:', request.method, request.url);
    return request;
  });
  
  apiClient.interceptors.response.use(
    response => {
      console.log('API Response:', response.status, response.config.url);
      return response;
    },
    error => {
      console.log('API Error:', error.response?.status, error.config?.url);
      return Promise.reject(error);
    }
  );
}
```

---

## Quick Reference

### Endpoints Summary

| Feature | Endpoint | Method | Auth |
|---------|----------|--------|------|
| Register | `/api/auth/register` | POST | No |
| Login | `/api/auth/login` | POST | No |
| Logout | `/api/auth/logout` | POST | Yes |
| Refresh | `/api/auth/refresh` | POST | No |
| Profile | `/api/users/me` | GET | Yes |
| Change Password | `/api/users/me/change-password` | POST | Yes |
| Send OTP | `/api/verification/otp/send` | POST | Yes |
| Verify OTP | `/api/verification/otp/verify` | POST | Yes |
| Send Email Verification | `/api/verification/email/send-verification` | POST | Yes |
| Verify Email | `/api/verification/email/verify` | GET | No |
| Forgot Password | `/api/verification/forgot-password` | POST | No |
| Reset Password | `/api/verification/reset-password` | POST | No |
| Google OAuth | `/oauth2/authorization/google` | GET | No |

### Response Format

**Success:**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "uuid...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

**Error:**
```json
{
  "timestamp": "2024-12-14T10:30:00",
  "status": 400,
  "error": "Validation Error",
  "message": "Invalid input",
  "validationErrors": {
    "email": "must be a valid email"
  }
}
```

---

**Questions?** Contact the FixHomi Engineering Team.
