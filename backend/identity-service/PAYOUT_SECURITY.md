# Payout Configuration Security Documentation

This document describes the security architecture for payout configuration in the Identity Service.

## Security Standards Compliance

### PCI-DSS (Payment Card Industry Data Security Standard)
- **Requirement 3.4**: Encryption at rest for sensitive account data
- **Requirement 10**: Audit logging for all access to sensitive data
- **Requirement 8**: Unique identification and authentication

### OWASP Top 10 (2021)
- **A03:2021 - Injection**: Input validation and sanitization
- **A01:2021 - Broken Access Control**: Owner-only authorization
- **A02:2021 - Cryptographic Failures**: AES-256-GCM encryption
- **A09:2021 - Security Logging Failures**: Comprehensive audit logging

### GDPR (General Data Protection Regulation)
- **Article 32**: Security of processing (encryption, pseudonymization)
- **Article 30**: Records of processing activities (audit logs)
- **Article 25**: Data protection by design and by default

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     PAYOUT CONFIGURATION SECURITY FLOW                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. AUTHORIZATION CHECK                                                     │
│  ───────────────────────                                                    │
│  Client → GraphQL Mutation → AuthorizationService.isOrganizationOwner()    │
│                                                                             │
│  ✅ ALLOWED: Organization OWNER                                            │
│  ❌ DENIED: Non-owners (403 Forbidden)                                     │
│                                                                             │
│  ┌────────────────────────────────────────────────────────────────────┐    │
│  │ Audit Log: PAYOUT_CONFIG_ACCESS_ATTEMPTED (if denied)              │    │
│  │ - User ID, IP address, timestamp                                   │    │
│  │ - Error: "Unauthorized: Only organization owner can modify"        │    │
│  └────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
│  2. INPUT VALIDATION (OWASP)                                                │
│  ────────────────────────────                                               │
│  FinancialDataValidator.validate(input)                                     │
│                                                                             │
│  Bank Account Number:                                                       │
│  - Pattern: ^[0-9]{10,16}$                                                  │
│  - Sanitization: Remove all non-digit characters                           │
│  - Example: "1234 5678 90" → "1234567890"                                  │
│                                                                             │
│  SWIFT Code:                                                                │
│  - Pattern: ^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$                            │
│  - Sanitization: Remove non-alphanumeric, uppercase                        │
│  - Example: "zana-zmlu" → "ZANAZMLU"                                       │
│                                                                             │
│  Phone Number (E.164):                                                      │
│  - Pattern: ^\+260(95|96|97|76|77)\d{7}$                                   │
│  - Normalization: 0971234567 → +260971234567                               │
│  - Validation: Zambian mobile prefixes only                                │
│                                                                             │
│  Account Holder Name:                                                       │
│  - Pattern: ^[a-zA-Z\s\-']{2,100}$                                         │
│  - Prevents: SQL injection, XSS, script injection                          │
│                                                                             │
│  ❌ Invalid input → 400 Bad Request (NO sensitive data in error)           │
│                                                                             │
│  3. ENCRYPTION (AES-256-GCM)                                                │
│  ────────────────────────────                                               │
│  FieldEncryptionService.encrypt(accountNumber)                              │
│                                                                             │
│  Algorithm: AES-256-GCM (Authenticated Encryption)                          │
│  - Confidentiality: AES-256 (symmetric key encryption)                     │
│  - Integrity: GCM authentication tag (prevents tampering)                  │
│                                                                             │
│  Encryption Process:                                                        │
│  1. Generate random IV (12 bytes, 96 bits)                                 │
│  2. Encrypt plaintext with AES-256-GCM                                     │
│  3. Prepend IV to ciphertext: [IV][Ciphertext][Auth Tag]                  │
│  4. Base64-encode for storage                                              │
│                                                                             │
│  Plaintext:  "1234567890123456"                                            │
│  Encrypted:  "A7xQ2...base64..." (stored in MongoDB)                       │
│  Display:    "****3456" (masked, PCI-DSS compliant)                        │
│                                                                             │
│  4. PERSISTENCE (MongoDB)                                                   │
│  ─────────────────────────                                                  │
│  Organization.payoutConfig.bankAccount.accountNumber = encrypted           │
│                                                                             │
│  Stored Data:                                                               │
│  {                                                                          │
│    "bankName": "Zanaco",                                                    │
│    "bankCode": "ZANAZMLU",                                                  │
│    "accountNumber": "A7xQ2...encrypted...",  ← ENCRYPTED                   │
│    "accountHolderName": "Example Business Ltd",                             │
│    "verified": false                                                        │
│  }                                                                          │
│                                                                             │
│  5. AUDIT LOGGING                                                           │
│  ─────────────────────                                                      │
│  PayoutConfigAuditService.logBankAccountChange(...)                         │
│                                                                             │
│  Audit Log Entry:                                                           │
│  {                                                                          │
│    "id": "audit-123",                                                       │
│    "organizationId": "org-456",                                             │
│    "userId": "user-789",                                                    │
│    "username": "john.doe",                                                  │
│    "action": "BANK_ACCOUNT_ADDED",                                          │
│    "timestamp": "2024-01-15T10:30:00Z",                                     │
│    "ipAddress": "192.168.1.100",                                            │
│    "userAgent": "Mozilla/5.0...",                                           │
│    "newAccountDetails": {                                                   │
│      "bankName": "Zanaco",                                                  │
│      "accountNumber": "****3456"  ← MASKED (PCI-DSS)                       │
│    },                                                                       │
│    "success": true                                                          │
│  }                                                                          │
│                                                                             │
│  ⚠️ CRITICAL: NEVER log plaintext account numbers                          │
│                                                                             │
│  6. GRAPHQL RESPONSE (MASKED)                                               │
│  ─────────────────────────────────                                          │
│  Client receives MASKED account number (PCI-DSS):                           │
│                                                                             │
│  {                                                                          │
│    "organization": {                                                        │
│      "payoutConfig": {                                                      │
│        "bankAccount": {                                                     │
│          "bankName": "Zanaco",                                              │
│          "accountNumber": "****3456",  ← MASKED                            │
│          "maskedAccountNumber": "****3456"                                  │
│        }                                                                    │
│      }                                                                      │
│    }                                                                        │
│  }                                                                          │
│                                                                             │
│  ⚠️ Full account number NEVER exposed via API                              │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Security Components

### 1. FinancialDataValidator (Input Validation)

**Location**: `com.pml.identity.validation.FinancialDataValidator`

**Purpose**: OWASP-compliant input validation and sanitization.

**Key Methods**:
```java
// Validate Zambian bank account (10-16 digits)
boolean isValid = FinancialDataValidator.isValidZambianAccountNumber("1234567890");

// Validate SWIFT/BIC code (8 or 11 alphanumeric)
boolean isValid = FinancialDataValidator.isValidSwiftCode("ZANAZMLU");

// Validate and normalize E.164 phone number
String normalized = FinancialDataValidator.normalizeToE164("0971234567");
// Result: "+260971234567"

// Validate account holder name (prevent injection)
boolean isValid = FinancialDataValidator.isValidAccountHolderName("John O'Brien");

// Mask account number (PCI-DSS)
String masked = FinancialDataValidator.maskAccountNumber("1234567890123456");
// Result: "****3456"

// Mask phone number (privacy)
String masked = FinancialDataValidator.maskPhoneNumber("+260971234567");
// Result: "+260****4567"
```

**Security Features**:
- Character whitelisting (prevents injection)
- Pattern matching (enforces format)
- Sanitization (removes dangerous characters)
- No sensitive data in error messages

---

### 2. FieldEncryptionService (Encryption)

**Location**: `com.pml.identity.security.FieldEncryptionService`

**Algorithm**: AES-256-GCM (Galois/Counter Mode)

**Why AES-GCM?**
- **Confidentiality**: AES-256 symmetric encryption
- **Integrity**: Authentication tag prevents tampering
- **Performance**: Better than AES-CBC + HMAC
- **PCI-DSS Approved**: Industry standard for payment data

**Encryption Process**:
```java
// Encrypt bank account number
Mono<String> encrypted = fieldEncryptionService.encrypt("1234567890");
// Result: "A7xQ2mY8...base64..." (IV prepended)

// Decrypt (for authorized users only)
Mono<String> decrypted = fieldEncryptionService.decrypt("A7xQ2mY8...");
// Result: "1234567890"
```

**Key Management**:
```bash
# Generate new encryption key (256-bit)
java -cp identity-service.jar FieldEncryptionService.generateKey()

# Output: "qRs7tUvWxYz0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P="
```

**Production Deployment**:

**Option 1: AWS KMS**
```bash
# Store key in AWS Secrets Manager
aws secretsmanager create-secret \
  --name payout-encryption-key \
  --secret-string "qRs7tUvWxYz0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P="

# Load in application
export APP_SECURITY_ENCRYPTION_KEY=$(aws secretsmanager get-secret-value \
  --secret-id payout-encryption-key \
  --query SecretString \
  --output text)
```

**Option 2: Azure Key Vault**
```bash
# Store key in Azure Key Vault
az keyvault secret set \
  --vault-name ticketing-vault \
  --name payout-encryption-key \
  --value "qRs7tUvWxYz0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P="

# Load in application
export APP_SECURITY_ENCRYPTION_KEY=$(az keyvault secret show \
  --vault-name ticketing-vault \
  --name payout-encryption-key \
  --query value \
  --output tsv)
```

**Option 3: HashiCorp Vault**
```bash
# Store key in Vault
vault kv put secret/payout-encryption-key \
  value="qRs7tUvWxYz0A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P="

# Load in application
export APP_SECURITY_ENCRYPTION_KEY=$(vault kv get \
  -field=value secret/payout-encryption-key)
```

**Key Rotation Strategy**:
1. Generate new key: `FieldEncryptionService.generateKey()`
2. Store new key in vault
3. Run migration script:
   - Decrypt all account numbers with old key
   - Re-encrypt with new key
   - Update MongoDB documents
4. Update `APP_SECURITY_ENCRYPTION_KEY` environment variable
5. Restart service
6. Archive old key (keep for 1 year for compliance)

---

### 3. PayoutConfigAuditService (Audit Logging)

**Location**: `com.pml.identity.service.PayoutConfigAuditService`

**PCI-DSS Requirement 10**: Track and monitor all access to cardholder data.

**Audit Log Types**:
```java
enum AuditAction {
    BANK_ACCOUNT_ADDED,          // Bank account added
    BANK_ACCOUNT_UPDATED,        // Bank account updated
    BANK_ACCOUNT_REMOVED,        // Bank account removed
    MOBILE_MONEY_ACCOUNT_ADDED,  // Mobile money account added
    MOBILE_MONEY_ACCOUNT_UPDATED,// Mobile money account updated
    PAYOUT_METHOD_CHANGED,       // Preferred payout method changed
    PAYOUT_ACCOUNT_VERIFIED,     // Admin verified payout account
    PAYOUT_CONFIG_ACCESS_ATTEMPTED, // Unauthorized access attempt
    PAYOUT_CONFIG_DECRYPTION_FAILED // Decryption failure (security incident)
}
```

**Usage Example**:
```java
// Log bank account change
auditService.logBankAccountChange(
    organizationId,
    userId,
    username,
    AuditAction.BANK_ACCOUNT_ADDED,
    previousBankAccount, // null if adding
    newBankAccount,      // MASKED account number
    ipAddress,
    userAgent
);
```

**Audit Log Storage**:
- **Collection**: `payout_config_audit_logs`
- **Retention**: 7 years (PCI-DSS requires minimum 1 year)
- **Indexes**: organizationId, userId, timestamp, action
- **CRITICAL**: Account numbers are MASKED (****3456)

**Querying Audit Logs**:
```java
// Get audit logs for organization
Flux<PayoutConfigAuditLog> logs = auditService.getAuditLogs(organizationId, 100);

// Get audit logs in time range (compliance reporting)
Flux<PayoutConfigAuditLog> logs = auditService.getAuditLogsInRange(
    organizationId,
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-12-31T23:59:59Z")
);
```

---

### 4. PayoutConfigMutationResolver (GraphQL API)

**Location**: `com.pml.identity.web.graphql.mutation.PayoutConfigMutationResolver`

**Mutations**:

#### `updatePayoutConfig`
```graphql
mutation {
  updatePayoutConfig(
    organizationId: "org-123"
    input: {
      preferredMethod: BANK_TRANSFER
      schedule: WEEKLY
      minimumPayoutAmount: 100.0
    }
  ) {
    id
    payoutConfig {
      preferredMethod
      schedule
      minimumPayoutAmount
    }
  }
}
```

**Authorization**: OWNER only

#### `setBankAccount`
```graphql
mutation {
  setBankAccount(
    organizationId: "org-123"
    input: {
      bankName: "Zanaco"
      bankCode: "ZANAZMLU"
      accountNumber: "1234567890"  # Encrypted before storage
      accountHolderName: "Example Business Ltd"
      accountType: "BUSINESS"
    }
  ) {
    id
    payoutConfig {
      bankAccount {
        bankName
        accountNumber  # Returns: "****7890"
        maskedAccountNumber
        verified
      }
    }
  }
}
```

**Authorization**: OWNER only
**Encryption**: Account number encrypted with AES-256-GCM
**Response**: Masked account number (****7890)

#### `setMobileMoneyAccount`
```graphql
mutation {
  setMobileMoneyAccount(
    organizationId: "org-123"
    input: {
      provider: MTN
      phoneNumber: "0971234567"  # Normalized to +260971234567
      accountHolderName: "John Doe"
    }
  ) {
    id
    payoutConfig {
      mobileMoneyAccount {
        provider
        phoneNumber  # Returns: "+260****4567"
        maskedPhoneNumber
        verified
      }
    }
  }
}
```

**Authorization**: OWNER only
**Validation**: E.164 format, Zambian mobile prefixes
**Response**: Masked phone number (+260****4567)

#### `verifyPayoutAccount` (Admin Only)
```graphql
mutation {
  verifyPayoutAccount(
    organizationId: "org-123"
    verified: true
  ) {
    id
    payoutConfig {
      verified
      canProcessPayouts
    }
  }
}
```

**Authorization**: ADMIN, FINANCE, SUPER_ADMIN only
**Audit Log**: PAYOUT_ACCOUNT_VERIFIED

---

## Security Testing

### Input Validation Tests

```java
// Test SQL injection prevention
assertThrows(IllegalArgumentException.class, () ->
    validator.isValidAccountHolderName("Robert'; DROP TABLE users;--")
);

// Test XSS prevention
assertThrows(IllegalArgumentException.class, () ->
    validator.isValidAccountHolderName("<script>alert('XSS')</script>")
);

// Test account number validation
assertTrue(validator.isValidZambianAccountNumber("1234567890"));
assertFalse(validator.isValidZambianAccountNumber("123")); // Too short
assertFalse(validator.isValidZambianAccountNumber("abc")); // Non-numeric

// Test E.164 phone validation
assertEquals("+260971234567", validator.normalizeToE164("0971234567"));
assertNull(validator.normalizeToE164("123")); // Invalid
```

### Encryption Tests

```java
// Test encryption/decryption
String plaintext = "1234567890123456";
String encrypted = encryptionService.encrypt(plaintext).block();
String decrypted = encryptionService.decrypt(encrypted).block();
assertEquals(plaintext, decrypted);

// Test tamper detection (GCM authentication)
String tampered = encrypted.substring(0, encrypted.length() - 10) + "XXXXXXXXXX";
assertThrows(EncryptionException.class, () ->
    encryptionService.decrypt(tampered).block()
);
```

### Authorization Tests

```java
// Test owner-only access
Mono<Organization> result = payoutConfigResolver.setBankAccount(
    "org-123",
    bankAccountInput,
    exchange
);

// Non-owner should fail
StepVerifier.create(result)
    .expectError(SecurityException.class)
    .verify();

// Owner should succeed
StepVerifier.create(result)
    .assertNext(org -> {
        assertNotNull(org.getPayoutConfig());
        assertTrue(org.getPayoutConfig().getBankAccount().getAccountNumber().startsWith("****"));
    })
    .verifyComplete();
```

---

## Monitoring & Alerts

### Security Events to Monitor

1. **Failed Authorization Attempts**
   - Query: `action: PAYOUT_CONFIG_ACCESS_ATTEMPTED AND success: false`
   - Alert: More than 5 failures in 1 hour
   - Action: Notify security team

2. **Decryption Failures**
   - Query: `action: PAYOUT_CONFIG_DECRYPTION_FAILED`
   - Alert: Any occurrence (potential key issue)
   - Action: Immediate investigation

3. **Payout Account Changes**
   - Query: `action: (BANK_ACCOUNT_ADDED OR MOBILE_MONEY_ACCOUNT_ADDED)`
   - Alert: New account added
   - Action: Admin review required for verification

4. **Verification Status Changes**
   - Query: `action: PAYOUT_ACCOUNT_VERIFIED`
   - Alert: Account verified
   - Action: Log for compliance audit

### SIEM Integration

Export audit logs to SIEM (Security Information and Event Management):

```bash
# Export to Elasticsearch
curl -X POST "http://elasticsearch:9200/payout-audit-logs/_doc" \
  -H "Content-Type: application/json" \
  -d @audit-log.json

# Export to Splunk
curl -X POST "https://splunk:8088/services/collector" \
  -H "Authorization: Splunk <token>" \
  -d @audit-log.json
```

---

## Compliance Checklist

### PCI-DSS v3.2.1

- [x] **Requirement 3.4**: Encryption at rest (AES-256-GCM)
- [x] **Requirement 3.5**: Key management procedures documented
- [x] **Requirement 10.1**: Audit trails for all access
- [x] **Requirement 10.2**: Automated audit trail for all users
- [x] **Requirement 10.3**: Audit trail entries include:
  - [x] User identification
  - [x] Type of event
  - [x] Date and time
  - [x] Success or failure
  - [x] Origination of event (IP address)
  - [x] Identity of affected resource

### OWASP Top 10 (2021)

- [x] **A01: Broken Access Control**: Owner-only authorization
- [x] **A02: Cryptographic Failures**: AES-256-GCM encryption
- [x] **A03: Injection**: Input validation and sanitization
- [x] **A09: Security Logging Failures**: Comprehensive audit logging

### GDPR

- [x] **Article 25**: Data protection by design (encryption, masking)
- [x] **Article 30**: Records of processing (audit logs)
- [x] **Article 32**: Security of processing (encryption, pseudonymization)

---

## Troubleshooting

### Issue: Decryption failure

**Error**: `EncryptionException: Failed to decrypt data`

**Possible Causes**:
1. Encryption key changed (key rotation not completed)
2. Data corrupted in database
3. Wrong key being used

**Solution**:
```bash
# Check encryption key
echo $APP_SECURITY_ENCRYPTION_KEY

# Verify key is 256-bit (32 bytes = 44 base64 characters)
echo $APP_SECURITY_ENCRYPTION_KEY | base64 -d | wc -c
# Should output: 32

# Test encryption/decryption
curl -X POST http://localhost:8083/api/internal/test/encrypt \
  -d '{"plaintext":"test"}'
```

### Issue: Unauthorized access

**Error**: `SecurityException: Only organization owner can modify payout configuration`

**Cause**: User is not the organization owner

**Solution**: Check organization ownership:
```graphql
query {
  organization(id: "org-123") {
    ownerId
    owner {
      id
      username
    }
  }
}
```

### Issue: Invalid account number

**Error**: `IllegalArgumentException: Invalid bank account number format`

**Cause**: Account number doesn't match validation pattern

**Solution**: Ensure account number:
- Contains only digits (0-9)
- Length between 10-16 characters
- No spaces, hyphens, or special characters

```javascript
// Frontend validation
const accountNumber = input.replace(/[^0-9]/g, ''); // Remove non-digits
if (accountNumber.length < 10 || accountNumber.length > 16) {
  throw new Error('Account number must be 10-16 digits');
}
```

---

## References

- [PCI-DSS v3.2.1](https://www.pcisecuritystandards.org/documents/PCI_DSS_v3-2-1.pdf)
- [OWASP Top 10 (2021)](https://owasp.org/Top10/)
- [OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html)
- [NIST SP 800-38D (AES-GCM)](https://csrc.nist.gov/publications/detail/sp/800-38d/final)
- [GDPR Articles](https://gdpr-info.eu/)
