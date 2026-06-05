# OWASP Security Compliance - MyTicket Zambia

This document outlines the OWASP security measures implemented across the MyTicket Zambia platform.

## OWASP Top 10 (2021) Compliance

### A01:2021 - Broken Access Control

| Component | Implementation | Status |
|-----------|---------------|--------|
| JWT Validation | OAuth2 Resource Server with Keycloak JWKS | ✅ |
| Role-Based Access | Keycloak realm roles (CUSTOMER, ORGANIZER, ADMIN) | ✅ |
| API Authorization | `@PreAuthorize` on GraphQL resolvers | ✅ |
| Internal API Protection | `/api/internal/**` blocked at gateway | ✅ |

### A02:2021 - Cryptographic Failures

| Component | Implementation | Status |
|-----------|---------------|--------|
| HTTPS Enforcement | HSTS header with 1-year max-age | ✅ |
| JWT Signing | RS256 with Keycloak-managed keys | ✅ |
| Password Hashing | bcrypt via Keycloak | ✅ |
| OTP Storage | Redis with TTL (not plaintext) | ✅ |

### A03:2021 - Injection

| Component | Implementation | Status |
|-----------|---------------|--------|
| Input Validation | Bean Validation on GraphQL inputs | ✅ |
| Request Size Limits | Max body 10MB, GraphQL 1MB | ✅ |
| Header Size Limits | Max 8KB per header | ✅ |
| URI Length Limits | Max 8KB URI | ✅ |
| NoSQL Injection | MongoDB parameterized queries | ✅ |

**API Gateway Configuration** (`application.yml`):
```yaml
server:
  max-http-request-header-size: 8KB
  netty:
    max-initial-line-length: 8192
spring:
  codec:
    max-in-memory-size: 10MB
```

### A04:2021 - Insecure Design

| Component | Implementation | Status |
|-----------|---------------|--------|
| Secure Architecture | Keycloak for auth (not custom) | ✅ |
| Defense in Depth | Gateway + Service validation | ✅ |
| Least Privilege | Role-based with minimal defaults | ✅ |

### A05:2021 - Security Misconfiguration

| Component | Implementation | Status |
|-----------|---------------|--------|
| Security Headers | `SecurityHeadersFilter.java` | ✅ |
| Server Version Hidden | Server header removed | ✅ |
| Actuator Limited | Only health/metrics exposed | ✅ |
| Default Credentials | Environment variables required | ✅ |

**Security Headers Added** (`SecurityHeadersFilter.java`):
```
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
X-Content-Type-Options: nosniff
X-Frame-Options: SAMEORIGIN
X-XSS-Protection: 1; mode=block
Content-Security-Policy: default-src 'self'; ...
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: accelerometer=(), camera=(), ...
Cache-Control: no-store, no-cache, must-revalidate
```

### A06:2021 - Vulnerable and Outdated Components

| Component | Version | Notes |
|-----------|---------|-------|
| Spring Boot | 3.5.4 | Latest stable |
| Keycloak | 26.5.2 | Latest stable |
| Java | 21 LTS | Long-term support |
| Netflix DGS | 10.0.1 | Latest stable |

### A07:2021 - Identification and Authentication Failures

| Component | Implementation | Status |
|-----------|---------------|--------|
| Brute Force Protection | `BruteForceProtectionFilter.java` | ✅ |
| Rate Limiting | Redis-based RequestRateLimiter | ✅ |
| Session Management | Keycloak-managed sessions | ✅ |
| Token Blacklist | Redis-based on logout | ✅ |
| OTP Rate Limiting | 60-second cooldown | ✅ |

**Brute Force Protection Configuration**:
```yaml
gateway:
  security:
    auth-rate-limit:
      enabled: true
      max-attempts: 5          # 5 attempts per window
      window-seconds: 300      # 5 minute window
      lockout-seconds: 900     # 15 minute lockout
```

**Protected Endpoints**:
- `/realms/event-ticketing/protocol/openid-connect/token`
- `/realms/event-ticketing/login-actions`
- `/oauth2/token`
- `/api/auth/login`
- `/api/auth/otp/verify`

### A08:2021 - Software and Data Integrity Failures

| Component | Implementation | Status |
|-----------|---------------|--------|
| JWT Validation | Signature verified with JWKS | ✅ |
| Webhook Signatures | Payment webhooks validated | ✅ |
| Dependency Integrity | Maven Central only | ✅ |

### A09:2021 - Security Logging and Monitoring Failures

| Component | Implementation | Status |
|-----------|---------------|--------|
| Request Logging | `RequestLoggingFilter.java` | ✅ |
| Security Event Logging | Keycloak event listeners | ✅ |
| Brute Force Alerts | Log on lockout trigger | ✅ |
| Correlation IDs | X-Correlation-Id header | ✅ |

### A10:2021 - Server-Side Request Forgery (SSRF)

| Component | Implementation | Status |
|-----------|---------------|--------|
| URL Validation | Internal API blocked at gateway | ✅ |
| Service Discovery | Internal DNS only | ✅ |

## Registration Security

### Account Type Role Mapper (`AccountTypeRoleMapper.java`)

**Input Validation**:
- Validates at least one account type selected
- Whitelist validation (only CUSTOMER/ORGANIZER allowed)
- Invalid types rejected with error message

**Role Assignment**:
- Validates realm roles exist before assignment
- Auto-creates missing roles with logged warning
- Always ensures CUSTOMER base role

**OWASP Compliance**:
- A01: Role assignment through Keycloak (not custom)
- A03: Input whitelist validation
- A07: Tied to Keycloak registration flow

## API Gateway Filters

| Filter | Order | Purpose |
|--------|-------|---------|
| `BruteForceProtectionFilter` | -150 | Block attackers before auth |
| `RequestSizeLimitFilter` | -100 | Prevent oversized requests |
| `OAuth2TokenRelayFilter` | 0 | Forward JWT to services |
| `SessionBlacklistFilter` | 10 | Block logged-out tokens |
| `RequestLoggingFilter` | 50 | Audit logging |
| `SecurityHeadersFilter` | 100 | Add security headers |

## Circuit Breakers (Resilience)

| Breaker | Failure Threshold | Timeout | Wait Duration |
|---------|-------------------|---------|---------------|
| apolloRouterCircuitBreaker | 30% | 45s | 10s |
| keycloakCircuitBreaker | 50% | 15s | 10s |

## Rate Limiting

| Endpoint Type | Rate | Burst |
|---------------|------|-------|
| Default | 100/sec | 200 |
| Payment Webhooks | 100/sec | 200 |
| Authentication | 5/5min | N/A |

## Keycloak Security Configuration

**Realm**: `event-ticketing`

**Password Policy**:
- Minimum length: 8 characters
- Requires uppercase
- Requires digits
- Requires special characters

**Brute Force Detection** (Keycloak-level):
- Max login failures: 30
- Wait increment: 60 seconds
- Max wait: 15 minutes

## Deployment Checklist

### Pre-Production

- [ ] Change all default passwords
- [ ] Enable HTTPS on all endpoints
- [ ] Configure production CORS origins
- [ ] Set up monitoring and alerting
- [ ] Enable Keycloak brute force detection
- [ ] Configure rate limits for expected traffic
- [ ] Move actuator to separate port (9001)

### Production Environment Variables

```bash
# Required for production
KEYCLOAK_ADMIN_PASSWORD=<strong-random>
POSTGRES_PASSWORD=<strong-random>
MONGO_PASSWORD=<strong-random>
REDIS_PASSWORD=<strong-random>
OTP_CLIENT_SECRET=<strong-random>

# Recommended for production
SPRING_PROFILES_ACTIVE=production
```

## Files Reference

| File | Purpose |
|------|---------|
| `GatewaySecurityConfig.java` | OAuth2 Resource Server config |
| `SecurityHeadersFilter.java` | OWASP security headers |
| `BruteForceProtectionFilter.java` | Authentication rate limiting |
| `RequestSizeLimitFilter.java` | Request size validation |
| `SessionBlacklistFilter.java` | Token blacklist check |
| `RequestLoggingFilter.java` | Audit logging |
| `application.yml` | Security configuration |
| `application-production.yml` | Production hardening |
