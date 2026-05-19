# Event Catalog Service

**Microservice 1 of 3** - Event Discovery & Management

## Overview

The Event Catalog Service is responsible for managing the entire lifecycle of events including creation, approval, publishing, and cancellation. This is the first microservice extracted from the monolithic event-ticketing system as part of the 3-microservices architecture migration.

### Bounded Context

**Event Discovery & Management**

- Event CRUD operations
- Venue management
- Event categorization and search
- Event approval workflow
- Event lifecycle management

### Collections Owned

- `events` - Main event data
- `venues` - Venue information
- `event_categories` - Event categorization
- `event_reminders` - Reminder notifications
- `approval_timelines` - Approval workflow tracking

### Events Published

- `EVENT_CREATED` - New event created by organizer
- `EVENT_APPROVED` - Event approved by admin
- `EVENT_REJECTED` - Event rejected by admin
- `EVENT_PUBLISHED` - Event published and available for tickets
- `EVENT_CANCELLED` - Event cancelled

## Architecture

### CDC Integration

The service receives Change Data Capture (CDC) events from a dedicated Debezium Server instance:

```
MongoDB (outbox_events) → Debezium Server → HTTP POST → Catalog Service Webhook
```

**Debezium Server Configuration:**
- Monitors `outbox_events` collection
- Filters events where `service == 'catalog'`
- Posts to webhook: `http://catalog-service:8081/api/cdc/events`
- Offset storage: Redis

### Technology Stack

- **Framework:** Spring Boot 3.5.4 (WebFlux - Reactive)
- **Language:** Java 21
- **Database:** MongoDB (shared database)
- **Build Tool:** Maven
- **Container:** Docker
- **Orchestration:** Kubernetes

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker
- MongoDB 5.0+
- Redis (for Debezium offset storage)

### Local Development

1. **Build the application:**
   ```bash
   mvn clean package
   ```

2. **Run locally:**
   ```bash
   java -jar target/catalog-service-1.0.0.jar
   ```

3. **Access endpoints:**
   - API: http://localhost:8081
   - GraphQL Playground: http://localhost:8081/graphiql
   - Health Check: http://localhost:8081/actuator/health
   - Metrics: http://localhost:8081/actuator/prometheus

### Docker Deployment

1. **Build Docker image:**
   ```bash
   docker build -t catalog-service:1.0.0 .
   ```

2. **Run container:**
   ```bash
   docker run -p 8081:8081 \
     -e MONGODB_URI=mongodb://localhost:27017/event_ticketing_prod \
     -e MONGODB_DATABASE=event_ticketing_prod \
     catalog-service:1.0.0
   ```

### Kubernetes Deployment

1. **Create MongoDB secret:**
   ```bash
   kubectl create secret generic mongodb-secret \
     --from-literal=connection-string='mongodb://user:pass@host:27017/db'
   ```

2. **Deploy Catalog Service:**
   ```bash
   kubectl apply -f k8s/deployment.yaml
   ```

3. **Deploy Debezium Server:**
   ```bash
   kubectl apply -f k8s/debezium-server-deployment.yaml
   ```

4. **Verify deployment:**
   ```bash
   kubectl get pods -l app=catalog-service
   kubectl get pods -l app=debezium-server,service=catalog
   ```

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `MONGODB_URI` | MongoDB connection string | `mongodb://localhost:27017/event_ticketing_prod` | Yes |
| `MONGODB_DATABASE` | Database name | `event_ticketing_prod` | Yes |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `default` | No |
| `JAVA_OPTS` | JVM options | `-Xms512m -Xmx1024m` | No |

### Application Properties

See `src/main/resources/application.yml` for full configuration options.

## API Documentation

### REST Endpoints

#### CDC Webhook (Internal)

```
POST /api/cdc/events
```
Receives CDC events from Debezium Server.

**Request Body:**
```json
{
  "schema": {...},
  "payload": {
    "_id": "event-id",
    "eventType": "EVENT_APPROVED",
    "aggregateType": "EVENT",
    "aggregateId": "event-123",
    "service": "catalog",
    "eventData": {...}
  }
}
```

#### Health Check

```
GET /api/cdc/health
```
Returns CDC webhook health status.

### GraphQL API

**Query events:**
```graphql
query {
  events(filter: { status: PUBLISHED }) {
    id
    title
    description
    startDate
    venue {
      name
      location
    }
  }
}
```

**Approve event:**
```graphql
mutation {
  approveEvent(eventId: "event-123", comments: "Looks good!") {
    success
    message
    event {
      id
      status
    }
  }
}
```

## Monitoring

### Health Checks

- **Liveness:** `/actuator/health/liveness`
- **Readiness:** `/actuator/health/readiness`
- **Detailed Health:** `/actuator/health`

### Metrics

Prometheus metrics are exposed at `/actuator/prometheus`.

**Key metrics:**
- `catalog_cdc_events_processed_total` - Total CDC events processed
- `catalog_cdc_events_failed_total` - Total CDC events failed
- `http_server_requests_seconds` - HTTP request duration

### Logging

Logs are output in JSON format to stdout for centralized logging systems.

**Log levels:**
```yaml
logging:
  level:
    root: INFO
    com.pml.catalog: DEBUG
```

## Scaling

### Horizontal Pod Autoscaler (HPA)

The service is configured with HPA:
- **Min replicas:** 2
- **Max replicas:** 5
- **CPU target:** 70%
- **Memory target:** 80%

### Resource Allocation

**Requests:**
- CPU: 250m
- Memory: 512Mi

**Limits:**
- CPU: 500m
- Memory: 1Gi

## Migration Status

- [x] Phase 1: Add service field to outbox_events (Completed)
- [x] Month 1-2: Create Catalog Service (In Progress)
  - [x] Spring Boot project structure
  - [x] CDC webhook controller
  - [x] Debezium Server configuration
  - [x] Kubernetes manifests
  - [ ] Business logic implementation
  - [ ] GraphQL schema
  - [ ] Integration tests
  - [ ] Deploy to staging
  - [ ] 10% traffic routing

## Related Services

- **Booking & Ticketing Service** (Port 8082) - Coming in Month 3-4
- **Platform & Identity Service** (Port 8083) - Coming in Month 5-6

## Support

For issues and questions, contact the platform team or open a GitHub issue.

## License

Proprietary - Event Ticketing Platform
