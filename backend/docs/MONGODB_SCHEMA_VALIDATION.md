# MongoDB Schema Validation Implementation Guide

## Overview

This document describes the application-layer MongoDB JSON Schema validation implementation for the Event Ticketing System. The validation enforces OWASP compliance and data integrity at the database level.

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    MONGODB SCHEMA VALIDATION ARCHITECTURE                │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Application Startup                                                     │
│       │                                                                  │
│       ▼                                                                  │
│  MongoSchemaValidationConfig (per service)                               │
│       │                                                                  │
│       ├─ Load JSON schemas from resources/mongodb/schemas/              │
│       ├─ Check if collection exists                                     │
│       │                                                                  │
│       ├─ If exists: Update schema (collMod command)                     │
│       └─ If not: Create with schema (createCollection)                  │
│                                                                          │
│  ┌────────────────────────────────────────────────────────┐             │
│  │  MongoDB (with schema validation enabled)              │             │
│  │                                                         │             │
│  │  Insert/Update attempts                                │             │
│  │       │                                                 │             │
│  │       ▼                                                 │             │
│  │  Document Validation                                   │             │
│  │       │                                                 │             │
│  │       ├─ Pass: Save document                           │             │
│  │       └─ Fail: Throw MongoWriteException (code 121)    │             │
│  └────────────────────────────────────────────────────────┘             │
│                                                                          │
│  MongoValidationErrorHandler                                             │
│       │                                                                  │
│       ├─ Parse MongoDB error response                                   │
│       ├─ Extract validation errors                                      │
│       ├─ Sanitize sensitive data                                        │
│       └─ Create MongoSchemaValidationException                          │
│                                                                          │
│  MongoValidationExceptionHandler (GraphQL)                               │
│       │                                                                  │
│       └─ Convert to GraphQL error with extensions                       │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `MongoSchemaValidationConfig` | shared-library | Base class for schema application |
| `MongoSchemaValidationProperties` | shared-library | Configuration properties |
| `MongoSchemaValidationException` | shared-library | Domain exception for validation failures |
| `MongoValidationErrorHandler` | shared-library | Parses MongoDB errors |
| `TenantValidationService` | shared-library | Validates tenant isolation |
| `MongoValidationExceptionHandler` | per service | GraphQL error mapping |

## Configuration

### Application Properties

Add to `application.yml` in each service:

```yaml
mongodb:
  schema-validation:
    # Enable/disable validation
    enabled: true

    # Validation action: ERROR (reject) or WARN (log only)
    validation-action: ERROR

    # Validation level: STRICT, MODERATE, or OFF
    validation-level: STRICT

    # Fail startup if validation cannot be applied
    fail-on-validation-error: false

    # Base path for schema files
    schema-base-path: mongodb/schemas

    # Operation timeout (milliseconds)
    operation-timeout-ms: 30000
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MONGODB_SCHEMA_VALIDATION_ENABLED` | `true` | Enable schema validation |
| `MONGODB_VALIDATION_ACTION` | `ERROR` | ERROR or WARN |
| `MONGODB_VALIDATION_LEVEL` | `STRICT` | STRICT, MODERATE, or OFF |
| `MONGODB_VALIDATION_FAIL_ON_ERROR` | `false` | Fail startup on error |

## Service-Specific Implementation

### Booking Service

**Collections with schemas:**
- `tickets` → `mongodb/schemas/tickets-schema.json`

**Configuration:**
```java
@Configuration
public class MongoSchemaValidationConfig extends com.pml.shared.config.MongoSchemaValidationConfig {

    @Override
    protected Map<String, String> getSchemaDefinitions() {
        Map<String, String> schemas = newSchemaMap();
        schemas.put("tickets", "tickets-schema.json");
        return schemas;
    }
}
```

### Identity Service

**Collections (schemas to be added):**
- `users` → `mongodb/schemas/users-schema.json`
- `organizers` → `mongodb/schemas/organizers-schema.json`
- `roles` → `mongodb/schemas/roles-schema.json`
- `permissions` → `mongodb/schemas/permissions-schema.json`

### Catalog Service

**Collections (schemas to be added):**
- `events` → `mongodb/schemas/events-schema.json`
- `locations` → `mongodb/schemas/locations-schema.json`
- `categories` → `mongodb/schemas/categories-schema.json`
- `ticket_tiers` → `mongodb/schemas/ticket-tiers-schema.json`

## JSON Schema Format

### File Structure

Schemas must be placed in `src/main/resources/mongodb/schemas/` of each service.

### Schema Template

```json
{
  "$jsonSchema": {
    "bsonType": "object",
    "required": ["field1", "field2"],
    "properties": {
      "_id": {
        "bsonType": "string",
        "description": "MongoDB document ID"
      },
      "organizationId": {
        "bsonType": "string",
        "pattern": "^[a-fA-F0-9]{24}$",
        "description": "OWASP A01: Tenant isolation required"
      },
      "email": {
        "bsonType": "string",
        "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
        "maxLength": 320,
        "description": "OWASP A03: Email validation"
      },
      "status": {
        "enum": ["ACTIVE", "INACTIVE", "SUSPENDED"],
        "description": "Status enum constraint"
      },
      "price": {
        "bsonType": "decimal",
        "description": "OWASP A04: Must be non-negative"
      },
      "createdAt": {
        "bsonType": "date",
        "description": "Timestamp"
      }
    },
    "additionalProperties": false
  }
}
```

### OWASP Compliance Patterns

| OWASP Category | Implementation |
|----------------|----------------|
| **A01 - Broken Access Control** | `organizationId` required field with ObjectId pattern |
| **A03 - Injection** | Email: `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`<br>Phone: `^\+[1-9]\d{1,14}$` (E.164)<br>URL: `^https?://.*` |
| **A04 - Insecure Design** | Price: `bsonType: "decimal"` with minimum validation<br>Enums: `enum: [...]` for status fields<br>Capacity: `minimum`, `maximum` constraints |
| **A09 - Security Logging** | String length limits: `maxLength` to prevent log overflow |

## Error Handling

### MongoDB Validation Error Flow

```
1. MongoDB rejects document → MongoWriteException (code 121)
2. MongoValidationErrorHandler.handleValidationError()
   - Parse MongoDB error response
   - Extract validation failures
   - Sanitize sensitive fields
3. Throw MongoSchemaValidationException
4. MongoValidationExceptionHandler (GraphQL)
   - Convert to GraphQL error
   - Add extensions with details
```

### GraphQL Error Response

```json
{
  "errors": [
    {
      "message": "Validation failed: Missing required fields: [organizationId]",
      "extensions": {
        "errorType": "VALIDATION_ERROR",
        "collection": "tickets",
        "validationErrors": [
          "Missing required fields: [organizationId]",
          "Field 'email': Invalid email format"
        ],
        "errorCode": 121,
        "classification": "BAD_REQUEST"
      }
    }
  ]
}
```

### Sensitive Data Sanitization

The `MongoValidationErrorHandler` automatically sanitizes:
- Password fields
- Tokens (access, refresh, API keys)
- Payment details (credit card, CVV, PIN)
- Email addresses (masked: `j***@example.com`)
- Phone numbers (masked: `+260***3456`)

## Tenant Validation Service

### Purpose

Prevents horizontal privilege escalation by validating `organizationId` matches the authenticated user's organization.

### Usage

```java
@Service
public class EventService {

    private final TenantValidationService tenantValidationService;
    private final EventRepository eventRepository;

    public Mono<Event> createEvent(CreateEventInput input, String organizationId) {
        Event event = mapper.toEntity(input);
        event.setOrganizationId(organizationId);

        return tenantValidationService.validateTenantContext(event, organizationId)
            .flatMap(eventRepository::save);
    }

    public Mono<Event> updateEvent(String eventId, UpdateEventInput input, String organizationId) {
        return eventRepository.findById(eventId)
            .flatMap(event -> tenantValidationService.validateTenantContext(event, organizationId))
            .flatMap(event -> {
                mapper.updateEntity(event, input);
                return eventRepository.save(event);
            });
    }
}
```

### Error Response

```json
{
  "errors": [
    {
      "message": "Access denied: Document belongs to organization 507f***9011, but user is in organization 6a2c***8d14",
      "extensions": {
        "errorType": "TENANT_ISOLATION_VIOLATION",
        "securityIncident": true,
        "classification": "PERMISSION_DENIED"
      }
    }
  ]
}
```

## Adding New Schemas

### Step 1: Create JSON Schema

Create `src/main/resources/mongodb/schemas/<collection>-schema.json`:

```json
{
  "$jsonSchema": {
    "bsonType": "object",
    "required": ["organizationId", "name"],
    "properties": {
      "_id": { "bsonType": "string" },
      "organizationId": {
        "bsonType": "string",
        "pattern": "^[a-fA-F0-9]{24}$"
      },
      "name": {
        "bsonType": "string",
        "minLength": 1,
        "maxLength": 200
      }
    },
    "additionalProperties": false
  }
}
```

### Step 2: Register in Configuration

Update `MongoSchemaValidationConfig`:

```java
@Override
protected Map<String, String> getSchemaDefinitions() {
    Map<String, String> schemas = newSchemaMap();
    schemas.put("tickets", "tickets-schema.json");
    schemas.put("payments", "payments-schema.json");  // NEW
    return schemas;
}
```

### Step 3: Restart Service

Schema validation is applied on application startup via `@EventListener(ApplicationReadyEvent.class)`.

## Testing Schema Validation

### Unit Test Example

```java
@SpringBootTest
@AutoConfigureWebTestClient
class TicketValidationTest {

    @Autowired
    private ReactiveMongoTemplate mongoTemplate;

    @Test
    void shouldRejectTicketWithoutOrganizationId() {
        Document invalidTicket = new Document()
            .append("ticketNumber", "TKT-12345678")
            .append("eventId", "507f1f77bcf86cd799439011")
            // Missing organizationId
            .append("price", new Decimal128(new BigDecimal("50.00")))
            .append("status", "PURCHASED");

        StepVerifier.create(
            mongoTemplate.insert(invalidTicket, "tickets")
        )
        .expectErrorMatches(ex ->
            ex instanceof MongoWriteException &&
            ((MongoWriteException) ex).getError().getCode() == 121
        )
        .verify();
    }

    @Test
    void shouldAcceptValidTicket() {
        Document validTicket = new Document()
            .append("_id", "ticket123")
            .append("ticketNumber", "TKT-12345678")
            .append("eventId", "507f1f77bcf86cd799439011")
            .append("organizationId", "507f1f77bcf86cd799439012")
            .append("buyerId", "507f1f77bcf86cd799439013")
            .append("eventTitle", "Test Event")
            .append("eventDate", "2026-06-01T19:00:00Z")
            .append("ticketCategory", "GENERAL_ADMISSION")
            .append("price", new Decimal128(new BigDecimal("50.00")))
            .append("status", "PURCHASED")
            .append("createdAt", new Date());

        StepVerifier.create(
            mongoTemplate.insert(validTicket, "tickets")
        )
        .expectNextCount(1)
        .verifyComplete();
    }
}
```

### Integration Test via GraphQL

```java
@Test
void shouldReturnValidationErrorFromGraphQL() {
    String mutation = """
        mutation {
          createTicket(input: {
            eventId: "507f1f77bcf86cd799439011"
            # Missing required organizationId
            price: 50.00
          }) {
            id
          }
        }
        """;

    webTestClient.post()
        .uri("/graphql")
        .bodyValue(Map.of("query", mutation))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.errors[0].extensions.errorType")
        .isEqualTo("VALIDATION_ERROR");
}
```

## Operational Considerations

### Startup Behavior

- Schema validation runs on `ApplicationReadyEvent`
- Errors are logged but don't fail startup (by default)
- Set `fail-on-validation-error: true` for strict enforcement

### Migration Strategy

For existing collections with invalid data:

1. **Phase 1: Audit Mode**
   ```yaml
   validation-action: WARN  # Log violations
   validation-level: STRICT
   ```

2. **Phase 2: Fix Data**
   - Query for invalid documents
   - Update to meet schema requirements
   - Use migrations or scripts

3. **Phase 3: Enforcement Mode**
   ```yaml
   validation-action: ERROR  # Reject violations
   validation-level: STRICT
   ```

### Performance Impact

- Schema validation adds minimal overhead (~1-2ms per write)
- Validation occurs server-side in MongoDB
- No network round-trips
- Indexed fields in validation patterns improve performance

### Monitoring

Log messages to watch:

```
INFO  - Schema validation applied to collection: tickets
WARN  - MongoDB validation failed for collection tickets: Missing required fields: [organizationId]
ERROR - SECURITY VIOLATION: Tenant isolation breach attempt
```

## Troubleshooting

### Schema Not Applied

**Symptom**: Collections accept invalid documents

**Solution**:
1. Check `mongodb.schema-validation.enabled=true`
2. Verify schema file exists at correct path
3. Check logs for schema loading errors
4. Restart application

### Schema File Not Found

**Error**: `Schema file not found: classpath:mongodb/schemas/tickets-schema.json`

**Solution**:
1. Ensure file is in `src/main/resources/mongodb/schemas/`
2. Check file name matches configuration
3. Rebuild project: `mvn clean package`

### Validation Fails for Valid Documents

**Symptom**: Documents that should be valid are rejected

**Solution**:
1. Check schema `required` fields match entity
2. Verify enum values match application constants
3. Test pattern regex expressions
4. Use `validation-level: MODERATE` temporarily

### Schema Update Not Applied

**Symptom**: Changes to schema JSON not reflected in MongoDB

**Solution**:
1. Restart application (schemas applied on startup)
2. Check MongoDB collection validator:
   ```javascript
   db.getCollectionInfos({name: "tickets"})
   ```
3. Manually update via `collMod` if needed

## Security Benefits

| Threat | Mitigation |
|--------|------------|
| **SQL Injection** | N/A (MongoDB uses BSON) |
| **NoSQL Injection** | Pattern validation for user inputs |
| **Horizontal Privilege Escalation** | `organizationId` required + TenantValidationService |
| **Data Type Confusion** | Strict `bsonType` enforcement |
| **Excessive Data Exposure** | `maxLength` limits prevent oversized fields |
| **Mass Assignment** | `additionalProperties: false` blocks unknown fields |

## References

- [MongoDB JSON Schema Validation](https://www.mongodb.com/docs/manual/core/schema-validation/)
- [OWASP Top 10 2021](https://owasp.org/Top10/)
- [Spring Data MongoDB Reactive](https://docs.spring.io/spring-data/mongodb/reference/mongodb/reactive-mongodb.html)
- [Netflix DGS Framework](https://netflix.github.io/dgs/)

## Next Steps

1. **Create remaining schemas** for identity-service and catalog-service collections
2. **Add integration tests** for all GraphQL mutations with validation
3. **Set up monitoring** for validation errors in production
4. **Document schema evolution strategy** for backward compatibility
5. **Implement schema versioning** for breaking changes
