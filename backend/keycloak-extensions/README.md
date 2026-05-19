# Keycloak Phone OTP Authenticator

Custom Keycloak authenticator for phone-based OTP authentication via WhatsApp/SMS.

## Overview

This authenticator enables passwordless login using phone numbers with OTP verification. It integrates with the Identity Service's existing OTP infrastructure (Redis + WhatsApp/Twilio).

## Features

- Phone number input with validation
- OTP delivery via WhatsApp (primary) or SMS (fallback)
- Rate limiting and cooldown protection
- Automatic user creation on first login
- Seamless integration with existing Keycloak flows

## Building

```bash
cd keycloak-phone-otp-authenticator
mvn clean package
```

This will create `target/phone-otp-authenticator-1.0.0.jar`.

## Deployment

### 1. Deploy to Keycloak

Copy the JAR to Keycloak's providers directory:

```bash
# For Keycloak in Docker
docker cp target/phone-otp-authenticator-1.0.0.jar keycloak:/opt/keycloak/providers/

# Restart Keycloak
docker restart keycloak
```

For Docker Compose, add a volume:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:24.0.1
  volumes:
    - ./keycloak-phone-otp-authenticator/target/phone-otp-authenticator-1.0.0.jar:/opt/keycloak/providers/phone-otp-authenticator.jar
  environment:
    - OTP_SERVICE_URL=http://identity-service:8083
```

### 2. Configure Environment Variables

The authenticator needs to know where the Identity Service is:

```bash
OTP_SERVICE_URL=http://identity-service:8083
```

### 3. Create Authentication Flow in Keycloak

After deploying the authenticator, create the authentication flow:

#### Via Admin Console

1. Go to **Authentication** > **Flows**
2. Click **Create flow**
3. Name: `phone-otp-browser`
4. Description: `Browser flow with phone OTP option`
5. Flow Type: `basic-flow`
6. Add executions:
   - **Cookie** (ALTERNATIVE)
   - **Identity Provider Redirector** (ALTERNATIVE)
   - Create sub-flow **Forms** (ALTERNATIVE)
     - **Username Password Form** (ALTERNATIVE)
     - **Phone OTP Authentication** (ALTERNATIVE)

7. Go to **Authentication** > **Bindings**
8. Set **Browser Flow** to `phone-otp-browser`

#### Via Keycloak Admin CLI

Run the setup script:

```bash
./scripts/setup-phone-otp-flow.sh
```

## Templates

The authenticator includes custom FreeMarker templates:

- `phone-otp-input.ftl` - Phone number input form with channel selection
- `phone-otp-verify.ftl` - OTP verification form with countdown timer

These templates use Keycloak's default styling and are automatically loaded from the JAR.

## Configuration Options

In the Keycloak admin console, you can configure:

| Option | Default | Description |
|--------|---------|-------------|
| OTP Service URL | `http://identity-service:8083` | Base URL of the Identity Service |
| Default Channel | `whatsapp` | Default OTP delivery channel |

## API Integration

The authenticator calls these Identity Service endpoints:

```
POST /api/internal/otp/request
POST /api/internal/otp/verify
GET  /api/internal/otp/status/{phoneNumber}
```

## User Flow

```
1. User clicks "Sign in with Phone"
2. User enters phone number
3. User selects delivery channel (WhatsApp/SMS)
4. System sends OTP via selected channel
5. User enters 6-digit OTP
6. System verifies OTP
7. If user doesn't exist, create new user with CUSTOMER role
8. User is authenticated and receives tokens
```

## Security Considerations

- OTPs expire after 5 minutes
- Maximum 3 verification attempts per OTP
- 60-second cooldown between OTP requests
- Phone numbers are stored in E.164 format
- Rate limiting applied at API Gateway level

## Troubleshooting

### Authenticator not appearing in Keycloak

1. Verify JAR is in `/opt/keycloak/providers/`
2. Check Keycloak logs for errors
3. Rebuild Keycloak: `docker exec keycloak /opt/keycloak/bin/kc.sh build`
4. Restart Keycloak

### OTP not being sent

1. Verify Identity Service is accessible
2. Check `OTP_SERVICE_URL` environment variable
3. Verify WhatsApp/Twilio credentials in Identity Service
4. Check Identity Service logs

### User creation failing

1. Ensure phone_number attribute is in user profile
2. Check CUSTOMER role exists
3. Verify database connectivity

## License

Proprietary - PML Technologies
