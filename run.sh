#!/usr/bin/env bash
#
# Run the SIGDEP-3 edge agent locally.
# spring-dotenv auto-loads ./.env at startup for application properties.
#
# Note: Spring decides which profile is active *before* property sources are
# attached, so SPRING_PROFILES_ACTIVE must be a real environment variable.
# We extract that one line from .env if present and export it ourselves;
# everything else flows through spring-dotenv.
#
# Usage:
#   ./run.sh           # run the packaged fat JAR (production-like)
#   ./run.sh --dev     # run via maven (faster restart, picks up code changes)
#

set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "ERROR: .env not found in $(pwd)"
  echo "       Copy .env.example to .env and fill in the values for this site."
  exit 1
fi

PROFILE_VALUE=$(grep -E '^[[:space:]]*SPRING_PROFILES_ACTIVE=' .env | tail -n1 | cut -d'=' -f2- || true)
if [[ -n "${PROFILE_VALUE:-}" ]]; then
  export SPRING_PROFILES_ACTIVE="$PROFILE_VALUE"
fi

if [[ "${1:-}" == "--dev" ]]; then
  exec mvn -B spring-boot:run
fi

JAR=target/sigdep-sync-0.1.0-SNAPSHOT.jar
if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run 'mvn -DskipTests package' first."
  exit 1
fi

exec java -jar "$JAR"
