# Report Service

A Spring Boot service that is part of the **CBA Clean** project: citizens
submit waste reports, which are persisted to PostgreSQL together with their
integration event and published asynchronously to RabbitMQ via a transactional
outbox.

## Responsibility

- `POST /api/v1/reports` - submit a new waste report (report + pending
  `ReportCreatedEvent` are committed in one PostgreSQL transaction).
- `GET /api/v1/reports/{id}` - read a report.
- Asynchronous delivery of `report.created` events through the outbox
  publisher (at-least-once; consumers deduplicate by `eventId`).

## Technologies

* Java 21
* Spring Boot 3.x
* Maven
* Spring Web
* PostgreSQL (JPA + Flyway)
* RabbitMQ (Spring AMQP with publisher confirms)
* Micrometer + Actuator
* JUnit 5, Testcontainers

## Transactional Outbox

The report and its integration event live or die together:

```
POST /api/v1/reports
        |
        v
one PostgreSQL transaction
  ├── reports
  └── outbox_events            (status PENDING)
        |
        v
Outbox Publisher (scheduled poll, batch claim via SKIP LOCKED)
        |  waits for RabbitMQ publisher confirm
        v
RabbitMQ (cba-clean.events / report.created)   -> outbox row becomes PUBLISHED
```

If RabbitMQ is unavailable, submissions still succeed and events remain
pending until the broker returns. Delivery is at-least-once; Incident Service
idempotency makes redeliveries harmless.

### Configuration

All settings are externalized under the `cbaclean.outbox` prefix with safe
local defaults:

| Property | Environment variable | Default | Meaning |
|---|---|---|---|
| `cbaclean.outbox.poll-interval` | `OUTBOX_POLL_INTERVAL` | `PT5S` | Delay between polling rounds; also bounds retry latency for failed publications. |
| `cbaclean.outbox.batch-size` | `OUTBOX_BATCH_SIZE` | `20` | Maximum number of events claimed per round. |
| `cbaclean.outbox.publish-confirm-timeout` | `OUTBOX_PUBLISH_CONFIRM_TIMEOUT` | `PT5S` | How long to wait for the broker's publisher confirm before treating an attempt as failed. |

## Running the application

```bash
./mvnw spring-boot:run
```

The application starts on port `8080`. Health check endpoint:

```text
GET http://localhost:8080/actuator/health
```

## Running the tests

```bash
./mvnw test
```

Integration tests use Testcontainers (real PostgreSQL and RabbitMQ containers),
including tests that verify report/outbox transaction atomicity, publisher
confirmations and retry after a broker outage.
