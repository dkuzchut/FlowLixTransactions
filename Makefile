.PHONY: up down run-core run-generator load-test test build

TOPIC ?= transaction-submitted

up:
	docker compose up -d postgres kafka platform-core

down:
	docker compose down

run-core:
	./gradlew :platform-core:bootRun

# Runs the generator as a one-shot CLI process.
run-generator:
	./gradlew :data-generator:run

# End-to-end load test using docker compose + SQL percentiles.
# Usage:
#   make load-test RATE=1000 DURATION=60
#   make load-test RAMP_RATE=3000 RAMP_DURATION=30
RAMP_RATE ?= 3000
RAMP_DURATION ?= 30
load-test: up
	docker compose build data-generator
	docker compose run --rm -e RATE=$(RATE) -e DURATION=$(DURATION) -e BOOTSTRAP_SERVERS=kafka:9092 -e TOPIC=$(TOPIC) data-generator
	docker compose exec -T postgres psql -U transactions -d transactions -v ON_ERROR_STOP=1 -c "\
WITH recent AS ( \
  SELECT EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000.0 AS latency_ms \
  FROM transactions \
  WHERE status IN ('SUCCEEDED','FAILED') \
    AND updated_at >= NOW() - (INTERVAL '1 second' * $(DURATION)) \
) \
SELECT \
  COUNT(*) AS finalized, \
  ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms)::numeric, 2) AS p50_ms, \
  ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms)::numeric, 2) AS p95_ms, \
  ROUND(MAX(latency_ms)::numeric, 2) AS max_ms \
FROM recent; \
"

# Ramp test: steady-state then ramp.
ramp-test: up
	docker compose build data-generator
	docker compose run --rm -e RATE=$(RATE) -e DURATION=$(DURATION) -e BOOTSTRAP_SERVERS=kafka:9092 -e TOPIC=$(TOPIC) data-generator
	docker compose run --rm -e RATE=$(RAMP_RATE) -e DURATION=$(RAMP_DURATION) -e BOOTSTRAP_SERVERS=kafka:9092 -e TOPIC=$(TOPIC) data-generator
	docker compose exec -T postgres psql -U transactions -d transactions -v ON_ERROR_STOP=1 -c "\
WITH recent AS ( \
  SELECT EXTRACT(EPOCH FROM (updated_at - created_at)) * 1000.0 AS latency_ms \
  FROM transactions \
  WHERE status IN ('SUCCEEDED','FAILED') \
    AND updated_at >= NOW() - (INTERVAL '1 second' * GREATEST($(DURATION), $(RAMP_DURATION))) \
) \
SELECT \
  COUNT(*) AS finalized, \
  ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY latency_ms)::numeric, 2) AS p50_ms, \
  ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms)::numeric, 2) AS p95_ms, \
  ROUND(MAX(latency_ms)::numeric, 2) AS max_ms \
FROM recent; \
"

test:
	./gradlew test

build:
	./gradlew build
