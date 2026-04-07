# FlowLix Transactions Task

Event-driven transaction processing system with two modules:

- **`platform-core`**: Spring Boot service consuming transactions from Kafka/Redpanda, persisting to Postgres, and finalizing status.
- **`data-generator`**: one-shot CLI tool that publishes synthetic `TransactionSubmitted` events to Kafka/Redpanda at a target rate.

## Quick start

### Prerequisites

- **Docker** (required for the simplest end-to-end run)
- **JDK 17** (only required if you want to run services via Gradle instead of Docker)

### Easiest end-to-end test (recommended)

From repo root, run an end-to-end load test:

```bash
make load-test RATE=1000 DURATION=60
```

This will:

- start **PostgreSQL**, **Redpanda (Kafka)**, and **platform-core** via Docker Compose
- run **data-generator** as a one-shot container to publish load into Kafka
- query Postgres for end-to-end latency percentiles (created_at → updated_at)

To try the 3k RPS target:

```bash
make load-test RATE=3000 DURATION=30
```

Or a quick ramp:

```bash
make ramp-test RATE=1000 DURATION=30 RAMP_RATE=3000 RAMP_DURATION=30
```

Stop everything when done:

```bash
make down
```

### Start services manually (Docker Compose)

If you want to interact with `platform-core` while it’s running:

```bash
docker compose up -d postgres kafka platform-core
```

This starts:

- **PostgreSQL** on `localhost:5432` (db/user/pass: `transactions`)
- **Redpanda** (Kafka-compatible) on `localhost:19092`
- **platform-core** on `http://localhost:8080`

Operational endpoints:

- **Health**: `http://localhost:8080/actuator/health`
- **Prometheus metrics**: `http://localhost:8080/actuator/prometheus`

### Run services via Gradle (dev mode)

Run `platform-core`:

Linux/macOS:

```bash
./gradlew :platform-core:bootRun
```

Windows:

```bat
gradlew.bat :platform-core:bootRun
```

## Generate load

`data-generator` is a one-shot process. You can run it via Docker (recommended for reproducibility) or via Gradle.

### Option A: Docker (recommended)

```bash
docker compose run --rm -e RATE=3000 -e DURATION=30 -e BOOTSTRAP_SERVERS=kafka:9092 -e TOPIC=transaction-submitted data-generator
```

### Option B: Gradle (dev)

Run the generator from repo root (one-shot; controlled via env vars):

Linux/macOS:

```bash
./gradlew :data-generator:run
```

Windows:

```bat
gradlew.bat :data-generator:run
```

Environment variables:

```bash
RATE=1000
DURATION=60
BOOTSTRAP_SERVERS=localhost:19092   # when running locally
TOPIC=transaction-submitted
KEYING=EXTERNAL_ID                  # EXTERNAL_ID | MERCHANT | ROUND_ROBIN
PARTITIONS=1
KAFKA_CONNECT_TIMEOUT_SECONDS=600   # set 0 to retry forever
```

## End-to-end load test

Use the Make targets (Docker Compose + generator + SQL percentiles):

```bash
make load-test RATE=3000 DURATION=30
```

## System behaviour (high-level)

1. Generator publishes `TransactionSubmittedEvent` JSON into Kafka topic `transaction-submitted`.
2. `platform-core` consumes, inserts a row with status `IN_PROGRESS` (idempotent via unique `external_id`).
3. A scheduled finalizer claims due rows in batches with `FOR UPDATE SKIP LOCKED` and updates to `SUCCEEDED`/`FAILED`.

## Configuration knobs

Primary config is `platform-core/src/main/resources/application.yml`.

Notable keys:

- **`spring.kafka.listener.concurrency`**: Kafka listener concurrency (default `4`)
- **`platform-core.finalizer-fixed-delay-ms`**: finalizer poll interval (default `75`)
- **`platform-core.finalize-batch-size`**: max rows finalized per batch (default `500`)

## Useful commands

- **Start services**: `make up`
- **Stop services**: `make down`
- **Unit tests**: `./gradlew test`
- **Full build**: `./gradlew build`

## Trade‑offs

This assignment intentionally balances realism with simplicity. Some of the most
important trade‑offs made in this codebase are listed below:

- **Event‑driven ingestion vs direct HTTP calls.** Using Kafka/Redpanda for
inbound transactions decouples producers from the core service and makes
load‑shaping trivial, but it adds another moving part and means the service
cannot be exercised via a simple REST call without a generator. An HTTP
ingress would be easier to demo but would need its own backpressure logic.
- **Immediate finalization vs configurable delay.** The current finalizer
processes any IN_PROGRESS row as soon as it is inserted. This yields
very low end‑to‑end latency but does not simulate a real payment processor
taking 1–2 seconds to complete. A previous version used a
processing_due_at timestamp and only finalized when that time was reached; that
approach introduces bounded lag but requires more scheduling logic and
database indexes.
- **Single scheduled worker.** Only one thread runs the finalizer loop,
polling every 75 ms. This keeps concurrency predictable and avoids
thread‑explosion on weak hardware, but it also becomes the bottleneck if
finalization of a batch takes longer than the poll interval. Scaling this
out horizontally would require coordinating across threads or services.
- **Batch updates vs per‑row updates.** The finalizer updates successes and
failures in two UPDATE … WHERE id IN (…) statements. This improves
throughput compared with updating each row individually but means a
transient failure could roll back an entire batch. A per‑row update model
would isolate failures at the cost of more DB round‑trips.
- **Unique constraint for idempotency.** Idempotency on external_id is
enforced via a unique constraint and ON CONFLICT DO NOTHING. This
approach is simple and reliable but forces every insert to check the index
and can become hot under very high duplicate rates. An explicit
idempotency key table would offer finer‑grained control.
- **Busy‑wait rate generation.** The generator uses LockSupport.parkNanos in
a tight loop to schedule sends. This yields accurate rates but burns CPU.
A more production‑like generator would use non‑blocking timers or a
reactive approach.

These compromises were chosen to satisfy the assignment requirements while
keeping the overall system easy to understand and run on a laptop.

## What I would do differently for production code

If this prototype were to evolve into a production‑grade service, several
improvements would be necessary:

1. **Expose an ingestion API and status endpoint.** Provide a
REST/HTTP endpoint (or gRPC) for clients to submit transactions with an
idempotency key and another endpoint to query the current status of a
transaction. The current design is queue‑only and requires direct DB
access to verify outcomes.

2. **Scale the finalizer horizontally.** Use a dedicated executor with
multiple threads or break finalization into its own microservice so that
throughput can scale with load. Alternatively, use partitioned finalizer
queues keyed by merchant to avoid contention.

3. **Exactly‑once semantics and outbox patterns.** Enable Kafka
transactions or adopt an outbox pattern to guarantee that a message is
processed and persisted exactly once, even across restarts. This reduces
the risk of duplicates and ensures atomicity between consuming and
persisting.

4. **Enhanced observability and tracing.** Instrument the code with
distributed tracing (OpenTelemetry/Zipkin) and include correlation IDs on
all logs and metrics. Provide dashboards for backlog size,
consumer lag, CPU/memory usage and end‑to‑end latency percentiles.

5. **Resilience and error handling.** Add dead‑letter topics for
unprocessable messages (already partially addressed with the DLQ) and
implement retries/backoff for database failures. Guarantee graceful
shutdown and draining of in‑flight work.

6. **More robust domain modelling.** Use proper monetary types and validate amounts and
merchant identifiers. Persist both the producer’s timestamp and the
server‑side processing timestamp to enable auditing and reconciliation.

7. **Comprehensive test coverage and benchmarks.** Write integration tests
using Testcontainers to verify correctness with real Postgres and
Redpanda. Add load tests (k6/Gatling) to measure p50/p95/p99 latencies
at different RPS levels. Automate these tests in CI.

8. **Resource limits and horizontal scaling.** In production, define
resource requests/limits for containers and deploy on a Kubernetes
platform. Use autoscaling policies based on lag or CPU to adjust
consumer and finalizer concurrency as needed.

9. **Security and compliance.** Authenticate producers and consumers,
encrypt data in transit and at rest, and implement auditing trails for
regulatory compliance. Add input validation and rate limiting on
ingestion endpoints to prevent abuse.