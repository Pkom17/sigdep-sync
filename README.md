# sigdep-sync

Edge agent deployed on each SIGDEP-3 site. Reads the local OpenMRS MySQL
database in read-only mode, buffers canonical records in a local SQLite
database, and pushes batches to `sigdep-hub` over HTTPS.

## Build

```bash
# Install sigdep-contracts first (sibling project)
cd ../sigdep-contracts && mvn -DskipTests install
cd ../sigdep-sync && mvn clean package
```

Produces an executable fat JAR in `target/`.

## Run (dev)

```bash
java -jar target/sigdep-sync-0.1.0-SNAPSHOT.jar
```

Override config via environment variables — see
[packaging/systemd/sigdep-sync.env.example](packaging/systemd/sigdep-sync.env.example).

## Deploy on a site

1. Copy the JAR to `/opt/sigdep-sync/sigdep-sync.jar`
2. Copy `packaging/systemd/sigdep-sync.service` to `/etc/systemd/system/`
3. Create `/etc/sigdep-sync/sigdep-sync.env` from the example and fill values
4. `systemctl enable --now sigdep-sync`
