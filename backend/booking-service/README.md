# Booking & Ticketing Service

**Microservice 2 of 3** - Ticket Inventory & Orders

## Overview

The Booking & Ticketing Service is the **highest-traffic microservice** responsible for ticket sales, inventory management, payment processing, and QR code generation. This service handles the critical path for all ticket purchases.

### Bounded Context

**Ticket Inventory & Orders**

- Ticket inventory management with optimistic locking
- Ticket purchase transactions
- Payment processing (PawaPay Mobile Money - MTN, Airtel, Zamtel)
- QR code generation for ticket validation
- Ticket validation at event entrance
- Refund and cancellation processing

### Collections Owned

- `tickets` - Individual ticket records
- `ticket_purchases` - Order/purchase records
- `payments` - Payment transaction details
- `ticket_inventory` - Real-time inventory tracking
- `qr_codes` - Generated QR codes for validation

### Events Published

- `TICKET_PURCHASED` - New ticket sale completed
- `PAYMENT_COMPLETED` - Payment provider confirmed payment
- `PAYMENT_FAILED` - Payment provider rejected payment
- `TICKET_VALIDATED` - Ticket scanned at event entrance
- `TICKET_REFUNDED` - Ticket refund processed
- `TICKET_CANCELLED` - Ticket cancelled

## Architecture

### CDC Integration

The service receives Change Data Capture (CDC) events from a dedicated Debezium Server instance:

```
MongoDB (outbox_events) → Debezium Server → HTTP POST → Booking Service Webhook
          ↓ Filter: service == 'booking'
```

**Debezium Server Configuration:**
- Monitors `outbox_events` collection
- Filters events where `service == 'booking'`
- Posts to webhook: `http://booking-service:8082/api/cdc/events`
- **High-volume optimization:** Larger batches (2000), more connections (20)
- Offset storage: Redis

### Technology Stack

- **Framework:** Spring Boot 3.5.4 (WebFlux - Reactive)
- **Language:** Java 21
- **Database:** MongoDB (shared database)
- **Cache:** Redis (inventory caching, distributed locking)
- **Build Tool:** Maven
- **Container:** Docker
- **Orchestration:** Kubernetes

### Key Features

1. **Optimistic Locking for Inventory**
   - Prevents overselling during concurrent purchases
   - Redis-backed distributed locking
   - Automatic inventory rollback on payment failure

2. **Payment Provider Integration**
   - PawaPay (unified mobile money API)
     - MTN Mobile Money (Zambia)
     - Airtel Money (Zambia)
     - Zamtel Kwacha (Zambia)
   - Idempotent payment processing
   - Automatic retry on transient failures
   - RFC-9421 HTTP Message Signature verification for webhooks

3. **QR Code Generation**
   - Async generation using job queue
   - Stored in AWS S3
   - Error correction level: M
   - Size: 300×300 pixels

4. **Refund Processing**
   - Time-based refund policies
   - Automatic calculation based on event date
   - Payment provider refund integration

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker
- MongoDB 5.0+
- Redis 7.x

### Local Development

1. **Build the application:**
   ```bash
   mvn clean package
   ```

2. **Run locally:**
   ```bash
   export MONGODB_URI=mongodb://localhost:27017/event_ticketing_prod
   export REDIS_HOST=localhost
   java -jar target/booking-service-1.0.0.jar
   ```

3. **Access endpoints:**
   - API: http://localhost:8082
   - GraphQL Playground: http://localhost:8082/graphiql
   - Health Check: http://localhost:8082/actuator/health
   - Metrics: http://localhost:8082/actuator/prometheus
   - CDC Metrics: http://localhost:8082/api/cdc/metrics

### Docker Deployment

1. **Build Docker image:**
   ```bash
   docker build -t booking-service:1.0.0 .
   ```

2. **Run container:**
   ```bash
   docker run -p 8082:8082 \
     -e MONGODB_URI=mongodb://localhost:27017/event_ticketing_prod \
     -e REDIS_HOST=redis \
     booking-service:1.0.0
   ```

### Kubernetes Deployment

1. **Create secrets:**
   ```bash
   # MongoDB
   kubectl create secret generic mongodb-secret \
     --from-literal=connection-string='mongodb://user:pass@host:27017/db'

   # Redis
   kubectl create secret generic redis-secret \
     --from-literal=password='redis-password'

   # PawaPay Payment Provider
   kubectl create secret generic pawapay-secrets \
     --from-literal=api-token='your-pawapay-api-token' \
     --from-literal=webhook-public-key='your-webhook-public-key'
   ```

2. **Deploy Booking Service:**
   ```bash
   kubectl apply -f k8s/deployment.yaml
   ```

3. **Deploy Debezium Server:**
   ```bash
   kubectl apply -f k8s/debezium-server-deployment.yaml
   ```

4. **Verify deployment:**
   ```bash
   kubectl get pods -l app=booking-service
   kubectl get pods -l app=debezium-server,service=booking

   # Check logs
   kubectl logs -l app=booking-service --tail=100 -f
   kubectl logs -l app=debezium-server,service=booking --tail=100 -f
   ```

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `MONGODB_URI` | MongoDB connection string | - | Yes |
| `MONGODB_DATABASE` | Database name | `event_ticketing_prod` | Yes |
| `REDIS_HOST` | Redis host | `localhost` | Yes |
| `REDIS_PORT` | Redis port | `6379` | No |
| `REDIS_PASSWORD` | Redis password | - | No |
| `PAWAPAY_API_URL` | PawaPay API URL | `https://api.sandbox.pawapay.io` | Yes |
| `PAWAPAY_API_TOKEN` | PawaPay API token | - | Yes |
| `PAWAPAY_WEBHOOK_PUBLIC_KEY` | PawaPay webhook public key | - | Yes |
| `CATALOG_SERVICE_URL` | Catalog service URL | `http://catalog-service:8081` | Yes |
| `IDENTITY_SERVICE_URL` | Identity service URL | `http://identity-service:8083` | Yes |

### Application Properties

See `src/main/resources/application.yml` for full configuration options.

**Key configurations:**
- `booking.inventory.reservation-timeout-minutes`: How long to hold reserved tickets (default: 15)
- `booking.payment.timeout-seconds`: Payment processing timeout (default: 30)
- `booking.qrcode.size`: QR code size in pixels (default: 300)
- `booking.refund.cutoff-hours-before-event`: Refund cutoff time (default: 24)

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
    "eventType": "TICKET_PURCHASED",
    "aggregateType": "TICKET",
    "aggregateId": "ticket-123",
    "service": "booking",
    "eventData": {
      "eventId": "evt-123",
      "buyerId": "user-456",
      "price": 50.00,
      "currency": "USD"
    }
  }
}
```

#### Health Check

```
GET /api/cdc/health
```
Returns CDC webhook health status.

#### Metrics

```
GET /api/cdc/metrics
```
Returns CDC processing statistics.

### GraphQL API

**Query tickets:**
```graphql
query {
  tickets(filter: { eventId: "evt-123" }) {
    id
    ticketNumber
    buyer {
      id
      email
    }
    price
    status
    qrCode
  }
}
```

**Purchase tickets:**
```graphql
mutation {
  purchaseTickets(input: {
    eventId: "evt-123"
    categoryId: "cat-456"
    quantity: 2
    paymentMethod: STRIPE
  }) {
    success
    purchaseId
    tickets {
      id
      ticketNumber
      qrCode
    }
  }
}
```

**Refund ticket:**
```graphql
mutation {
  refundTicket(id: "ticket-123", reason: "Event cancelled") {
    success
    refundAmount
    status
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
- `booking_cdc_events_processed_total` - Total CDC events processed
- `booking_cdc_events_failed_total` - Total CDC events failed
- `booking_ticket_purchases_total` - Total tickets sold
- `booking_payment_success_rate` - Payment success rate
- `booking_inventory_updates_total` - Inventory update operations
- `http_server_requests_seconds` - HTTP request duration

**CDC-specific metrics:**
```
booking_cdc_events_processing_seconds{type="TICKET_PURCHASED"} 0.045
booking_cdc_events_per_second 150
```

### Logging

Logs are output in structured format to stdout.

**Log levels:**
```yaml
logging:
  level:
    root: INFO
    com.pml.booking: DEBUG
```

## Scaling

### Horizontal Pod Autoscaler (HPA)

The service is configured with aggressive scaling:
- **Min replicas:** 3
- **Max replicas:** 10
- **CPU target:** 70%
- **Memory target:** 80%
- **Custom metric:** CDC events per second (target: 100)

### Resource Allocation

**Requests:**
- CPU: 500m
- Memory: 1Gi

**Limits:**
- CPU: 1000m
- Memory: 2Gi

### Scaling Behavior

- **Scale up:** Add 3 pods at a time during flash sales
- **Scale down:** Gradually reduce (50% per minute)
- **Stabilization:** 5-minute window before scaling down

## Performance

### Expected Traffic

- **Normal load:** 10-50 CDC events/second
- **Peak load:** 100-1000 CDC events/second (flash sales)
- **Average response time:** < 50ms (p95)
- **QR code generation:** < 200ms (async)

### Optimization Strategies

1. **Redis Caching**
   - Cache ticket inventory (60-second TTL)
   - Cache event details (10-minute TTL)
   - Reduces MongoDB load by 80%

2. **Connection Pooling**
   - MongoDB: 20 connections
   - Redis: 10 connections
   - HTTP client: 20 connections

3. **Async Processing**
   - QR code generation (non-blocking)
   - Notification triggers (fire-and-forget)
   - Analytics updates (eventual consistency)

4. **Batch Operations**
   - Debezium batch size: 2000 events
   - Bulk inventory updates
   - Batch notification sending

## Troubleshooting

### High CDC Event Latency

**Symptoms:**
- Tickets not showing as sold immediately
- Debezium lag increasing

**Solutions:**
1. Check Debezium Server logs:
   ```bash
   kubectl logs -l app=debezium-server,service=booking --tail=100
   ```

2. Increase Debezium batch size:
   ```properties
   debezium.source.max.batch.size=3000
   ```

3. Scale booking service:
   ```bash
   kubectl scale deployment booking-service --replicas=5
   ```

### Payment Processing Failures

**Symptoms:**
- High `booking_payment_failed_total` metric
- Tickets stuck in RESERVED state

**Solutions:**
1. Check payment provider status
2. Review payment service logs:
   ```bash
   kubectl logs -l app=booking-service | grep "PAYMENT_FAILED"
   ```

3. Verify payment provider credentials:
   ```bash
   kubectl get secret payment-secrets -o jsonpath='{.data}'
   ```

### Inventory Overselling

**Symptoms:**
- More tickets sold than capacity
- Inventory count negative

**Solutions:**
1. Enable optimistic locking (should be on by default):
   ```yaml
   booking:
     inventory:
       optimistic-locking: true
   ```

2. Check for race conditions in logs
3. Review Redis connection stability

## Migration Status

- [x] Phase 1: Add service field to outbox_events (Completed)
- [x] Month 3-4: Create Booking Service (In Progress)
  - [x] Spring Boot project structure
  - [x] CDC webhook controller with metrics
  - [x] Debezium Server configuration (high-volume optimized)
  - [x] Kubernetes manifests with HPA
  - [x] Docker configuration
  - [ ] Implement inventory management logic
  - [x] Integrate payment provider (PawaPay Mobile Money)
  - [ ] Implement QR code generation
  - [ ] Write integration tests
  - [ ] Deploy to staging environment
  - [ ] Configure 10% traffic routing

## Related Services

- **Event Catalog Service** (Port 8081) - Event discovery & management
- **Platform & Identity Service** (Port 8083) - Coming in Month 5-6

## Support

For issues and questions, contact the platform team or open a GitHub issue.

## License

Proprietary - Event Ticketing Platform
