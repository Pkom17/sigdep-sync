#!/usr/bin/env bash
#
# Run the SIGDEP-3 edge agent locally.
# spring-dotenv auto-loads ./.env at startup for application properties.
#
# Note: Spring decides which profile is active *before* property sources are
# attached, so SPRING_PROFILES_ACTIVE must be a real environment variable.
# Same goes for JAVA_HOME, which has to point at a JDK 17+ install. We
# extract those two lines from .env if present and export them ourselves;
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

# Pull a single KEY=value out of .env (last occurrence wins, blank if missing).
read_from_env() {
  grep -E "^[[:space:]]*$1=" .env | tail -n1 | cut -d'=' -f2- || true
}

# --- Resolve JAVA_HOME (JDK 17+) -------------------------------------------
# .env wins over a system JAVA_HOME (which often points at the wrong version
# on dev machines using SDKMAN / asdf / jenv). If .env is silent, try macOS
# /usr/libexec/java_home -v 17. Otherwise fall back to the inherited
# JAVA_HOME and let the version check below catch any mismatch.
JAVA_HOME_FROM_ENV=$(read_from_env JAVA_HOME)
if [[ -n "${JAVA_HOME_FROM_ENV:-}" ]]; then
  export JAVA_HOME="$JAVA_HOME_FROM_ENV"
elif [[ "$(uname -s)" == "Darwin" ]] && command -v /usr/libexec/java_home >/dev/null; then
  if AUTO=$(/usr/libexec/java_home -v 17 2>/dev/null); then
    export JAVA_HOME="$AUTO"
  fi
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN=$(command -v java || true)
fi

if [[ -z "${JAVA_BIN:-}" || ! -x "$JAVA_BIN" ]]; then
  echo "ERROR: no java executable found. Set JAVA_HOME in .env to a JDK 17+ install."
  exit 1
fi

JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d'.' -f1)
if [[ -z "$JAVA_VERSION" || "$JAVA_VERSION" -lt 17 ]]; then
  echo "ERROR: $JAVA_BIN is Java $JAVA_VERSION; this project requires JDK 17 or newer."
  echo "       Set JAVA_HOME in .env to a JDK 17+ install (e.g. /usr/libexec/java_home -v 17)."
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# --- Resolve SPRING_PROFILES_ACTIVE ----------------------------------------
PROFILE_VALUE=$(read_from_env SPRING_PROFILES_ACTIVE)
if [[ -n "${PROFILE_VALUE:-}" ]]; then
  export SPRING_PROFILES_ACTIVE="$PROFILE_VALUE"
fi

# --- Run --------------------------------------------------------------------
if [[ "${1:-}" == "--dev" ]]; then
  exec mvn -B spring-boot:run
fi

JAR=target/sigdep-sync-0.1.0-SNAPSHOT.jar
if [[ ! -f "$JAR" ]]; then
  echo "ERROR: $JAR not found. Run 'mvn -DskipTests package' first."
  exit 1
fi

exec "$JAVA_BIN" -jar "$JAR"
