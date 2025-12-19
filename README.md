# FixHomi Authentication Service

Production-grade JWT-based authentication microservice for FixHomi application.

## 🏗️ Architecture

- **Spring Boot**: 3.4.12
- **Java**: 17 (LTS)
- **Security**: Spring Security 6.x + JWT (jjwt 0.11.5)
- **Database (Dev)**: H2 in-memory
- **Database (Prod)**: PostgreSQL (ready to configure)
- **Build Tool**: Maven
- **Authentication**: Stateless JWT tokens

## 📦 Project Structure

```
com.fixhomi.auth
├── config/              # Spring configuration classes
│   ├── JpaConfig.java         # JPA auditing configuration
│   └── SecurityConfig.java    # Security & CORS configuration
├── controller/          # REST API controllers
│   └── AuthController.java    # Authentication endpoints
├── dto/                 # Data Transfer Objects
│   ├── LoginRequest.java      # Login request payload
│   ├── LoginResponse.java     # Login response with JWT
│   └── RegisterRequest.java   # Registration request payload
├── entity/              # JPA entities
│   ├── Role.java             # User role enum
│   └── User.java             # User entity
├── exception/           # Custom exceptions & handlers
│   ├── AuthenticationException.java
│   ├── DuplicateResourceException.java
│   ├── ErrorResponse.java
│   ├── GlobalExceptionHandler.java
│   └── ResourceNotFoundException.java
├── repository/          # Data access layer
│   └── UserRepository.java   # User repository interface
├── security/            # Security components
│   ├── JwtAuthenticationFilter.java  # JWT validation filter
│   └── JwtService.java               # JWT generation/validation
└── service/             # Business logic
    └── AuthService.java              # Authentication service
```

## 👥 User Roles

The system supports five distinct user types:

- `USER` - Regular customers
- `SERVICE_PROVIDER` - Service providers (plumbers, electricians, etc.)
- `ADMIN` - Administrative users with full access
- `SUPPORT` - Customer support staff
- `IT_ADMIN` - IT administrators for system management

## 🔐 Security Features

### JWT Token Structure

```json
{
  "userId": 123,
  "role": "USER",
  "tokenType": "ACCESS",
  "sub": "user@example.com",
  "iat": 1234567890,
  "exp": 1234654290,
  "iss": "fixhomi-auth-service"
}
```

### Key Security Components

1. **Password Encryption**: BCrypt with strength 12
2. **JWT Signing**: HS512 algorithm
3. **Token Expiration**: 24 hours (configurable)
4. **Stateless Authentication**: No server-side sessions
5. **CORS Configuration**: Pre-configured for Node.js services
6. **Role-Based Access Control**: Spring Security method-level security

## 🚀 API Endpoints

### Public Endpoints (No Authentication Required)

#### 1. Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "phoneNumber": "+1234567890",
  "password": "SecurePass123!",
  "fullName": "John Doe",
  "role": "USER"
}
```

**Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

#### 2. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "tokenType": "Bearer",
  "userId": 1,
  "email": "user@example.com",
  "fullName": "John Doe",
  "role": "USER",
  "expiresIn": 86400
}
```

#### 3. Health Check
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

### Protected Endpoints (Require JWT)

To access protected endpoints, include the JWT token in the Authorization header:

```http
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

## 🔧 Configuration

### application.yaml

Key configurations in `src/main/resources/application.yaml`:

```yaml
# JWT Configuration
jwt:
  secret: your-256-bit-secret-key-change-this-in-production
  expiration:
    ms: 86400000  # 24 hours
  issuer: fixhomi-auth-service

# Database (H2 for development)
spring:
  datasource:
    url: jdbc:h2:mem:fixhomi_auth
    username: sa
    password: 
```

### Production Configuration

For production, update:

1. **JWT Secret**: Use a strong, randomly generated 256-bit key
2. **Database**: Switch to PostgreSQL
3. **CORS Origins**: Configure allowed origins in `SecurityConfig.java`
4. **Logging**: Reduce log level to INFO or WARN

## 🗄️ Database Schema

### Users Table

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT |
| email | VARCHAR(100) | NOT NULL, UNIQUE |
| phone_number | VARCHAR(20) | |
| password_hash | VARCHAR(60) | NOT NULL (BCrypt) |
| full_name | VARCHAR(100) | NOT NULL |
| role | VARCHAR(20) | NOT NULL (ENUM) |
| is_active | BOOLEAN | NOT NULL, DEFAULT TRUE |
| is_email_verified | BOOLEAN | NOT NULL, DEFAULT FALSE |
| is_phone_verified | BOOLEAN | NOT NULL, DEFAULT FALSE |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |
| last_login_at | TIMESTAMP | |

### Indexes

- `idx_email` - Unique index on email
- `idx_phone` - Index on phone_number

## 🧪 Testing with cURL

### Register a User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!",
    "fullName": "John Doe",
    "role": "USER"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePass123!"
  }'
```

### Access Protected Resource
```bash
curl -X GET http://localhost:8080/api/protected-endpoint \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## 🔗 Integration with Node.js Services

Node.js services can validate JWT tokens using the same secret key:

```javascript
const jwt = require('jsonwebtoken');

function verifyToken(token) {
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    return {
      userId: decoded.userId,
      email: decoded.sub,
      role: decoded.role,
      tokenType: decoded.tokenType
    };
  } catch (error) {
    throw new Error('Invalid token');
  }
}

// Middleware example
function authenticate(req, res, next) {
  const authHeader = req.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ error: 'No token provided' });
  }
  
  const token = authHeader.substring(7);
  
  try {
    req.user = verifyToken(token);
    next();
  } catch (error) {
    res.status(401).json({ error: 'Invalid token' });
  }
}
```

## 🚦 Running the Application

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Start the Application
```bash
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

### Access H2 Console (Development)
```
URL: http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:fixhomi_auth
Username: sa
Password: (leave empty)
```

## 📋 Error Handling

All errors return a consistent JSON structure:

```json
{
  "timestamp": "2024-12-14T10:30:00",
  "status": 401,
  "error": "Authentication Failed",
  "message": "Invalid email or password",
  "path": "/api/auth/login",
  "validationErrors": {
    "field": "error message"
  }
}
```

### HTTP Status Codes

- `200 OK` - Successful request
- `201 Created` - Resource created (registration)
- `400 Bad Request` - Validation errors
- `401 Unauthorized` - Authentication failed
- `403 Forbidden` - Access denied
- `404 Not Found` - Resource not found
- `409 Conflict` - Duplicate resource (email exists)
- `500 Internal Server Error` - Server error

## 🔒 Security Best Practices

1. **Never commit the JWT secret** to version control
2. **Use environment variables** for sensitive configuration
3. **Rotate JWT secrets** periodically in production
4. **Implement rate limiting** for login endpoints
5. **Use HTTPS** in production
6. **Monitor failed login attempts** for security threats
7. **Implement refresh tokens** for long-lived sessions (future enhancement)

## 📝 Next Steps (Future Enhancements)

Once the core authentication is confirmed working:

1. ✅ **Refresh Tokens** - Add refresh token support
2. ✅ **Email Verification** - Implement email verification flow
3. ✅ **Password Reset** - Add forgot password functionality
4. ✅ **2FA/MFA** - Two-factor authentication
5. ✅ **OAuth2 Integration** - Google, Facebook login
6. ✅ **Rate Limiting** - Prevent brute force attacks
7. ✅ **Audit Logging** - Track authentication events
8. ✅ **User Management APIs** - Admin endpoints for user CRUD

## 🤝 Contributing

This is a production microservice. All changes must:
- Follow clean code principles
- Include proper validation
- Have comprehensive error handling
- Be properly tested

---

**Service Owner**: FixHomi Engineering Team  
**Last Updated**: December 2024  
**Version**: 1.0.0
