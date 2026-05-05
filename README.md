# sigdep-sync

Edge agent deployed on each SIGDEP-3 site. Reads the local OpenMRS MySQL
database in read-only mode, buffers canonical records in a local SQLite
database, and pushes batches to `sigdep-hub` over HTTPS.

## Configuration

All runtime configuration lives in a `.env` file at the project root.
`spring-dotenv` loads it automatically at startup, so the same file works on
Linux, macOS and Windows without any wrapper.

```bash
cp .env.example .env
# edit .env — at minimum set SIGDEP_SITE_CODE
```

The systemd unit (`packaging/systemd/sigdep-sync.service`) reads the same
file format via `EnvironmentFile=`. In production, install the file to
`/etc/sigdep-sync/sigdep-sync.env` (or whatever path you prefer) and adjust
the unit accordingly.

## Build

```bash
# Install sigdep-contracts first (sibling project)
cd ../sigdep-contracts && mvn -DskipTests install
cd ../sigdep-sync && mvn clean package
```

Produces an executable fat JAR in `target/`.

## Run

Linux / macOS:

```bash
./run.sh         # runs the packaged JAR (production-like)
./run.sh --dev   # runs via maven (faster restart while iterating)
```

Windows:

```bat
run.bat
run.bat --dev
```

Both forms read `.env` automatically.

## Deploy on a site

1. Copy the JAR to `/opt/sigdep-sync/sigdep-sync.jar`
2. Copy `packaging/systemd/sigdep-sync.service` to `/etc/systemd/system/`
3. Copy `.env.example` to `/etc/sigdep-sync/sigdep-sync.env` and fill values
4. `systemctl enable --now sigdep-sync`

For Windows sites, wrap the JAR with WinSW or NSSM and point its config at
the `.env` file. (Service tooling for Windows is on the roadmap, not yet
provided in this repo.)
