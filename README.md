# sigdep-sync

Edge agent deployed on each SIGDEP-3 site. Reads the local OpenMRS MySQL
database in read-only mode, buffers canonical records in a local SQLite
database (outbox + watermarks), and pushes batches to `sigdep-hub` over
HTTPS.

## Place in the SIGDEP-3 platform

This repo is one of three projects that make up SIGDEP-3:

| Project                                                            | Role                                                                    |
| ------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| [`sigdep-contracts`](https://github.com/ITECH-CI/sigdep-contracts) | Shared DTOs and API contracts (Maven library)                           |
| **`sigdep-sync`** (this repo)                                      | Edge agent deployed on each site — reads local OpenMRS, pushes batches  |
| [`sigdep-hub`](https://github.com/ITECH-CI/sigdep-hub)             | Central server — receives batches, indicators, console                  |

This agent is what makes a site visible on the national console. One
JVM process per site, running continuously, talking to:

- **OpenMRS MySQL** locally on the site network (read-only).
- A **SQLite buffer** on disk for offline tolerance and resumability.
- The **central hub** over HTTPS, authenticated as the `sigdep-agent`
  Keycloak client (client-credentials).

```
   ┌──────────────────┐     ┌─────────────── sigdep-sync ────────────────┐
   │  site OpenMRS    │     │                                              │
   │  (MySQL, read)   │◄────┤ extractors ─► SQLite buffer ─► pusher        │
   └──────────────────┘     │                  (outbox)        │           │
                            │                                  │  HTTPS    │
                            └──────────────────────────────────┼───────────┘
                                                               │ JWT
                                                               ▼
                                                     sigdep-hub /api/v1/sync/*
```

## What it extracts

Each `*Extractor` under `src/main/java/.../sync/extractor/` reads one
OpenMRS table (or a join), promotes well-known concepts to typed columns,
keeps the rest in `extra_data`, and emits a record from
`sigdep-contracts`:

| Extractor              | Source                                  | Hub entity                  |
| ---------------------- | --------------------------------------- | --------------------------- |
| `PatientExtractor`     | `patient`, `person`, `person_name`, …   | `core.patients`             |
| `VisitExtractor`       | `encounter` of follow-up types          | `core.visits`               |
| `InitiationExtractor`  | "PEC - Mise sous traitement" encounters | `core.treatment_initiations`|
| `ClosureExtractor`     | "PEC - Issue" encounters                | `core.closures`             |
| `LabResultExtractor`   | `obs` for lab concepts                  | `core.lab_results`          |
| `TptExtractor`         | "PEC - Suivi TPT" + "PEC - Issue TPT"   | `core.tpt_records`          |

Watermarks (date of the last record successfully sent for each entity)
are kept in the SQLite buffer, so the agent resumes where it left off
after a restart or a network outage.

## Configuration

All runtime configuration lives in a `.env` file at the project root.
`spring-dotenv` loads it automatically at startup, so the same file
works on Linux, macOS and Windows without any wrapper.

```bash
cp .env.example .env
# edit .env — at minimum set SIGDEP_SITE_CODE, SIGDEP_LOCAL_DB_PASSWORD,
# SIGDEP_CENTRAL_API_URL and SIGDEP_KEYCLOAK_CLIENT_SECRET.
```

Key variables:

| Variable                             | Purpose                                              |
| ------------------------------------ | ---------------------------------------------------- |
| `SIGDEP_SITE_CODE`                   | DHIS2 facility code — must exist in `core.sites`     |
| `SIGDEP_LOCAL_DB_URL`                | JDBC URL of the OpenMRS MySQL database               |
| `SIGDEP_LOCAL_DB_USER` / `_PASSWORD` | A **read-only** account on the OpenMRS DB            |
| `SIGDEP_BUFFER_PATH`                 | Where the SQLite buffer lives (persistent volume)    |
| `SIGDEP_CENTRAL_API_URL`             | Base URL of `sigdep-hub` (the ingestion-api)         |
| `SIGDEP_KEYCLOAK_URL`                | Base URL of the hub's Keycloak                       |
| `SIGDEP_KEYCLOAK_CLIENT_SECRET`      | Secret for the `sigdep-agent` confidential client    |
| `SIGDEP_SYNC_INTERVAL_MINUTES`       | How often to run a sync cycle (default 15 min)       |
| `SIGDEP_BATCH_SIZE`                  | Max records per HTTP call (default 500)              |

The systemd unit (`packaging/systemd/sigdep-sync.service`) reads the
same file format via `EnvironmentFile=`. In production, install the
file at `/etc/sigdep-sync/sigdep-sync.env` and adjust the unit
accordingly.

## Build

```bash
# Install sigdep-contracts first (sibling project)
git clone https://github.com/ITECH-CI/sigdep-contracts
cd sigdep-contracts && mvn -DskipTests install && cd ..

# Build the agent
git clone https://github.com/ITECH-CI/sigdep-sync
cd sigdep-sync && mvn clean package
```

Produces an executable fat JAR at `target/sigdep-sync-*.jar`.

## Run locally

```bash
./run.sh           # runs the packaged JAR (production-like)
./run.sh --dev     # runs via maven (faster restart while iterating)
```

Windows:

```bat
run.bat
run.bat --dev
```

Both forms read `.env` automatically. The agent logs every cycle to
stdout — pipe it to a file in production.

For a local end-to-end test, point
`SIGDEP_CENTRAL_API_URL=http://localhost:9000` at a running `sigdep-hub`
dev stack (see that repo's README).

## Deploy on a site (Linux)

1. Copy the JAR to `/opt/sigdep-sync/sigdep-sync.jar`.
2. Copy `packaging/systemd/sigdep-sync.service` to
   `/etc/systemd/system/`.
3. Copy `.env.example` to `/etc/sigdep-sync/sigdep-sync.env` and fill
   the site-specific values.
4. Create the buffer directory:
   `mkdir -p /var/lib/sigdep-agent && chown sigdep-agent:sigdep-agent /var/lib/sigdep-agent`.
5. `systemctl enable --now sigdep-sync`.
6. Tail logs: `journalctl -u sigdep-sync -f`.

## Deploy on a site (Windows)

A native Windows wrapper isn't shipped yet. Common options:

- **WinSW** (recommended) — drop a small XML config next to the JAR and
  run `winsw.exe install`. Wraps it as a Windows service that survives
  reboots.
- **NSSM** — same idea, more flexible UI.

Both can point at the `.env` file as the source of environment
variables. A reference config is on the roadmap; for now, copy from a
similar Java service on the target host.

## Operations

### Watch the buffer

```bash
sqlite3 /var/lib/sigdep-agent/buffer.sqlite '.schema'
sqlite3 /var/lib/sigdep-agent/buffer.sqlite \
  'SELECT entity_type, COUNT(*) FROM outbox GROUP BY entity_type;'
sqlite3 /var/lib/sigdep-agent/buffer.sqlite \
  'SELECT entity_type, last_watermark FROM watermarks;'
```

### Force a fresh full sync

Stop the agent, delete the buffer file, restart. The agent will replay
everything from the beginning (slow on a large site — expect hours).

```bash
systemctl stop sigdep-sync
rm /var/lib/sigdep-agent/buffer.sqlite
systemctl start sigdep-sync
```

### Increase the batch size for backfills

If the site is far behind and the default 500 records/cycle is too
slow, bump `SIGDEP_BATCH_SIZE` to 20000 temporarily and restart. The
hub handles batches up to a few tens of thousands without issue.

### "Site code not found" rejections

The site code in `SIGDEP_SITE_CODE` must match a row in `core.sites` on
the hub. Either the site hasn't been seeded yet, or the value is
wrong. Don't make the agent create rows — sites are reference data and
live in the hub's seed migration.

## License

To be decided in a plenary session with the HMIS TWG; no license file
is shipped yet. In the meantime, treat the contents as "all rights
reserved by I-TECH Côte d'Ivoire and the PNLS programme".
