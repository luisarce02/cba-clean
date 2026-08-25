# cba-clean

CBA Clean is a portfolio project for citizen waste reports: citizens submit
reports (Report Service, PostgreSQL), which are published as integration events
over RabbitMQ and turned into incidents (Incident Service, MongoDB).

## Architecture at a glance

```
citizen -> Report Service (PostgreSQL)
               |  publishes ReportCreatedEvent
               v
           RabbitMQ  (cba-clean.events exchange)
               |
               v
           Incident Service (MongoDB) - consumes report.created,
           opens incidents, idempotent via processed_events claims
```

## Messaging architecture

- **Main exchange**: `cba-clean.events` (topic, durable) -
  the Report Service publishes `ReportCreatedEvent` with routing key
  `report.created`.
- **Main queue**: `incident-service.report-created` (durable), bound to the
  main exchange. It is declared with dead-letter routing
  (`x-dead-letter-exchange=cba-clean.dlx`,
  `x-dead-letter-routing-key=incident-service.report-created.dlq`) so that
  deliveries rejected without requeue by the listener container itself (e.g.
  fatally malformed JSON that can never be deserialized) land directly in the
  DLQ.
- **Retry strategy**: bounded TTL retry queues. On a transient processing
  failure the consumer republishes the message to the dead-letter exchange with
  a per-retry routing key; each retry queue waits its configured TTL and then
  dead-letters the message back to `cba-clean.events/report.created`, producing
  a delayed redelivery:

  ```
  incident-service.report-created.retry.1   (TTL 2s)
  incident-service.report-created.retry.2   (TTL 4s)
  incident-service.report-created.retry.3   (TTL 8s)
  ```

- **Retry limits**: 3 retries after the initial delivery (up to 4 attempts).
  The retry count travels in an `x-retry-count` message header. When the limit
  is reached, the message is routed to the DLQ instead of another retry.
- **Dead-letter exchange / queue**: `cba-clean.dlx` (topic, durable) routes
  into `incident-service.report-created.dlq` (durable). All retry and DLQ
  infrastructure lives under `infrastructure/messaging`
  (`MessagingTopology`, `ReportCreatedEventRetryRouter`).
- **Poison messages** are never retried: unknown report types or priorities
  (`EventTranslationException`) and structurally invalid JSON go straight to
  the DLQ, where they remain available for inspection.
- **Configuration**: `incident.messaging.retry.max-retries` (default `3`) and
  `incident.messaging.retry.delays` (default `2s,4s,8s`); overridable via
  environment (`INCIDENT_RETRY_MAX_RETRIES`, `INCIDENT_RETRY_DELAYS`).

### Relationship with idempotency

Idempotency is unchanged and claim-first: before invoking the use case, the
consumer atomically claims the event's `eventId` in MongoDB
(`processed_events`). A consequence worth knowing: if processing fails *after*
the claim was consumed, the retried delivery is skipped as already claimed -
no incident is created for it. Retries therefore repair transient failures up
to and including the claim (e.g. MongoDB briefly unavailable); closing the
claim-to-persist window would require transactional writes or the Outbox
Pattern, which is out of scope here.

### One-time migration note

The main queue's arguments changed (it now carries dead-letter declarations).
An existing RabbitMQ volume still holds the old argument-less declaration, so
one upgrade requires deleting the old queue (or running
`docker compose down -v` for local development) before starting the new
incident service.

## Running the stack

```bash
docker compose up -d --build
```

- Report Service API: http://localhost:8080/swagger-ui.html
- Incident Service health: http://localhost:8081/actuator/health
- RabbitMQ management UI: http://localhost:15672 (cbaclean/cbaclean)

## Running the tests

```bash
cd backend/report-service  && ./mvnw test
cd backend/incident-service && ./mvnw test
```

Incident Service integration tests use Testcontainers (real RabbitMQ and
MongoDB containers), including tests for retry counts, configured delays, DLQ
routing of poison messages and idempotent duplicate handling.
