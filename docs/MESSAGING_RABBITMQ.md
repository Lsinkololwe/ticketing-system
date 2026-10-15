# Messaging: RabbitMQ

Cross-service facts travel over RabbitMQ (ROADMAP D-62; the event contract is
`specs/_platform/003-event-contract/spec.md`). Azure Service Bus is not used anywhere.

## The shape

```
service transaction ──► outbox row (MongoDB, same transaction)
                              │  drain (after commit, outside any transaction)
                              ▼
                     EventBridge ──► AmqpEventTransport ── publisher confirm ──► topic exchange
                                                                                   │ routing key = wire name
                                  ┌────────────────────────────────────────────────┤
                                  ▼                                                ▼
                       <exchange>.<group> queue                          <exchange>.<group> queue
                       (quorum, TTL 1 h, DLX)                            (quorum, TTL 1 h, DLX)
                                  │ consumer acks, or rejects without requeue
                                  ▼
                       <exchange>.<group>.dlq   (bounded at 10 000, oldest dropped)
```

| Exchange | Publisher | Queues |
|---|---|---|
| `catalog-events` | catalog-service | `catalog-events.booking-sub`, `catalog-events.identity-sub` |
| `booking-events` | booking-service | `booking-events.catalog-sub`, `booking-events.identity-sub` |
| `identity-events` | identity-service | `identity-events.catalog-sub`, `identity-events.booking-sub` |

A queue is bound once per wire name it consumes (`catalog.EventPublished`, …). A wire name no queue
binds is accepted by the exchange and dropped. The whole topology is code (`EventTopology` in
shared-library) and every service declares all of it on startup, idempotently, so no service needs
another to have started first.

## Guarantees

- **A message is published only after its transaction commits** (the outbox), and a row is marked sent
  only when the broker has **confirmed** the publish. An unreachable broker leaves the row pending.
- **At-least-once.** Every consumer deduplicates on `eventId` (`ConsumerGuard`).
- **Ordering.** A subscription that carries an ordered row has a single active consumer and a
  prefetch of one, so it hands messages over in publish order. Order is per *subscription*, not per
  aggregate: a slow message delays the ones behind it. Every message carries `orderingKey` for the day
  that has to be narrowed.
- **Failure.** A consumer retries within its budget; past it, the message is rejected without a
  requeue and the queue's dead-letter exchange moves it to `<queue>.dlq` with its body intact, where an
  operator can replay it.

## Ordering and when to revisit it

Ordering is guaranteed per subscription, not per entity: a subscription that carries an ordered wire
name has one active consumer, so its messages are handled one at a time in publish order. That is
enough while a single consumer keeps up.

**Revisit trigger (no poller; read it from the broker's own metrics):** when an ordered
subscription's queue depth stays above 1 000 messages for ten minutes, or its oldest message waits
longer than five minutes, one consumer is no longer enough. The change at that point is to partition
by the event's ordering key (a consistent-hash exchange in front of N single-active-consumer queues),
not to remove the ordering. Until the trigger is met, do not add partitions.

## Running it locally

The broker is `dev_rabbitmq` in docker-resources, shared with Twende. The ticketing system has its own
virtual host `ticketing` and user, created by the `dev_rabbitmq_init` job:

```
cd docker-resources
docker compose --profile ticketing-local up -d        # or: --profile rabbitmq
```

The services read `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_VHOST`, `RABBITMQ_USERNAME` and
`RABBITMQ_PASSWORD`; none has a default, and the `local` profile pins the development values. Browse
queues and dead letters at http://localhost:15672.

## Tests

Tests run a real broker (`RabbitNode`, `rabbitmq:4-management-alpine`, the compose image) on a
virtual host of their own: `RabbitEventTransportTest` (routing, ordering, dead-lettering, confirms),
`OutboxToBrokerTest` (MongoDB to broker), `CatalogEventListenerTest` (booking's listener with Redis),
`EventTopologyTest` (the registry against the topology) and `NoServiceBusLintTest`.
