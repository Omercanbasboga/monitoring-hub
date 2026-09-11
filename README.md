# Monitoring Hub

A Spring Boot service that unifies four different real-world ways external
sensor/environmental data shows up in practice into one clean model and one
API, instead of building a separate stack per source:

| Connector | Pattern | Real-world shape |
|---|---|---|
| `RestPollingConnector` | REST/JSON polling | Per-sensor time-range GET, with a backfill sweep for anything a slow response caused the live poll to miss |
| `CsvFeedConnector` | Bulk CSV over HTTP | One wide CSV per fetch: a timestamp column + one value column per station |
| `DelimitedFtpConnector` | FTP + delimited file | Per-station file dropped on an FTP server, header row + metadata rows + comma-separated readings |
| `BinaryFeedConnector` | FTP + custom binary format | Hourly-bucketed **GCF** (Guralp Compressed Format) files: diff-encoded, big-endian, block-structured binary time series |

Every connector writes into the same `Station` / `Reading` tables through one
shared `StationDataService`, so gap-filling (`GapFiller`) and chart
downsampling (`ChartDownsampler`, min-max bucketing) are each implemented
**once** and reused by all four sources — not copy-pasted per source.

A companion project, [`minmax-lttb-downsampler`](https://github.com/Omercanbasboga/minmax-lttb-downsampler),
extracts the downsampling idea (plus an LTTB pass) as a standalone,
framework-free library.

## Why this project exists

This is a personal, generic reimplementation of an architecture I built for
a university sea-level/environmental monitoring system during my time there.
The production system, its data-source names, its infrastructure, and its
credentials are that institution's property and are **not** reproduced here.
What you're looking at is the same real engineering — REST/CSV/FTP/binary
ingestion, unification, gap-filling, downsampling, a DB-backed dynamic
scheduler — rewritten from scratch against a made-up domain so it can live in
a public personal portfolio.

## Highlights worth reading first

- `ingestion/binary/GcfBinaryParser.java` — from-scratch binary parser for a
  real compressed sensor format: big-endian block layout, diff-decoding
  against a running integration constant, bit-packed sampling-rate and
  timestamp decoding. Covered by a byte-level unit test that hand-builds a
  block and checks the decoded values and timestamp exactly.
- `service/ChartDownsampler.java` — min-max bucketing downsampler with a test
  that asserts a single spike buried in 30,000 points survives being reduced
  to 300 buckets (the failure mode plain every-Nth-point sampling doesn't
  protect against).
- `config/DynamicSchedulingConfig.java` + `DynamicIntervalTrigger.java` +
  `SchedulerConfigService.java` — each source's polling interval lives in the
  DB and can be changed at runtime through `PUT /api/scheduler/intervals/{source}`
  without a restart.

## Running it

```bash
mvn spring-boot:run
```

Starts on `:8082` against an in-memory H2 database — no setup required.
Swagger UI: `http://localhost:8082/swagger-ui.html`.

Every connector is **opt-in**: with no configuration, all four simply stay
idle (no crashes, no exceptions — they no-op when their URL/host is blank).
Point one at a real feed via environment variables, e.g.:

```bash
export CSV_SOURCE_FEED_URL=https://example.org/realtime.csv
mvn spring-boot:run
```

See `application.properties` for the full list of `connectors.*` settings
and their corresponding environment variables. The Postgres profile
(`application-docker.properties`) takes every credential from the
environment as well — there are no default or example credentials anywhere
in this repository.

## API

- `GET /api/unified/stations` — every station across every configured source, with its latest reading
- `GET /api/unified/stations/{sourceType}` — filtered by `REST_API` / `CSV_FEED` / `FTP_DELIMITED` / `FTP_BINARY`
- `GET /api/sources/{sourceType}/stations/{externalId}/readings` — raw, gap-filled readings for a time range
- `GET /api/sources/{sourceType}/stations/{externalId}/readings/chart/last-24h` — gap-filled + downsampled, ready to plot
- `GET /api/scheduler/intervals`, `PUT /api/scheduler/intervals/{source}` — read/tune polling intervals

## Stack

Java 21, Spring Boot 3.5, Spring Data JPA, H2 (dev) / PostgreSQL (docker profile), springdoc-openapi, Apache Commons Net (FTP), JUnit 5.
