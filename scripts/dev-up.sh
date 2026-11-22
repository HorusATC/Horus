#!/usr/bin/env bash
# Start Backend (bootRun) and Frontend (npm run dev) for local quickstart.
# Requires Java 21 and Node.js 18+. Ctrl-C stops both processes.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.dev-logs"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
BACKEND_PID=""
FRONTEND_PID=""

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

java_major_version() {
  # Args: optional JAVA_HOME, or empty to use PATH java.
  local home="${1:-}"
  local java_bin
  if [ -n "$home" ]; then
    java_bin="${home}/bin/java"
  else
    java_bin="$(command -v java 2>/dev/null || true)"
  fi
  [ -x "$java_bin" ] || return 1
  # Prefer -version (always prints to stderr). Avoid picking 22/24.
  local ver_line
  ver_line="$("$java_bin" -version 2>&1 | head -n 1)" || return 1
  # Match "21" as the major (21, 21.0.x). Reject 22 and 24 explicitly via pattern.
  if printf '%s' "$ver_line" | grep -Eq 'version "21([.\"]|$)'; then
    printf '%s\n' "21"
    return 0
  fi
  return 1
}

is_java21_home() {
  local home="$1"
  [ -n "$home" ] && [ -x "${home}/bin/java" ] || return 1
  java_major_version "$home" >/dev/null 2>&1
}

resolve_java21() {
  local candidate=""

  # 1) JAVA_HOME if it is 21
  if [ -n "${JAVA_HOME:-}" ] && is_java21_home "$JAVA_HOME"; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  # 2) macOS java_home
  if [ -x /usr/libexec/java_home ]; then
    candidate="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    if [ -n "$candidate" ] && is_java21_home "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  # 3) SDKMAN candidates
  if [ -d "${HOME}/.sdkman/candidates/java" ]; then
    for candidate in "${HOME}/.sdkman/candidates/java"/*21*; do
      [ -d "$candidate" ] || continue
      if is_java21_home "$candidate"; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  fi

  # 4) /usr/lib/jvm/*21*
  if [ -d /usr/lib/jvm ]; then
    for candidate in /usr/lib/jvm/*21*; do
      [ -d "$candidate" ] || continue
      if is_java21_home "$candidate"; then
        printf '%s\n' "$candidate"
        return 0
      fi
    done
  fi

  # 5) ~/jdk21
  if is_java21_home "${HOME}/jdk21"; then
    printf '%s\n' "${HOME}/jdk21"
    return 0
  fi

  # 6) java on PATH if it is 21
  if java_major_version >/dev/null 2>&1; then
    # Prefer JAVA_HOME reported by the binary when available.
    candidate="$(dirname "$(dirname "$(command -v java)")")"
    if is_java21_home "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
    # Fall back: export a synthetic home from the binary path.
    printf '%s\n' "$candidate"
    return 0
  fi

  return 1
}

print_java_install_help() {
  cat >&2 <<'EOF'
Java 21 was not found.

Install Eclipse Temurin 21, then re-run this script. Examples:

  # macOS (Homebrew)
  brew install --cask temurin@21

  # Linux (see Adoptium for your distro)
  # https://adoptium.net/temurin/releases/?version=21

  # Or unpack Temurin 21 to ~/jdk21 so this script can find it:
  #   mkdir -p ~/jdk21 && tar -xzf OpenJDK21U-jdk_*.tar.gz -C ~/jdk21 --strip-components=1

Do not use Java 22 or Java 24 (incompatible with this project).
EOF
}

ensure_node() {
  command -v node >/dev/null 2>&1 || die "Node.js is required (18+). Install from https://nodejs.org/"
  command -v npm >/dev/null 2>&1 || die "npm is required. Install Node.js 18+ from https://nodejs.org/"
  local major
  major="$(node -p 'process.versions.node.split(".")[0]')"
  if [ "$major" -lt 18 ]; then
    die "Node.js 18+ is required (found $(node --version))."
  fi
}

needs_npm_ci() {
  local fe="${ROOT_DIR}/Frontend"
  if [ ! -d "${fe}/node_modules" ]; then
    return 0
  fi
  # Stale when lockfile is newer than node_modules (or npm's recorded lock).
  if [ "${fe}/package-lock.json" -nt "${fe}/node_modules" ]; then
    return 0
  fi
  if [ -f "${fe}/node_modules/.package-lock.json" ] \
    && ! cmp -s "${fe}/package-lock.json" "${fe}/node_modules/.package-lock.json" 2>/dev/null; then
    return 0
  fi
  return 1
}

wait_for_url() {
  local url="$1"
  local name="$2"
  local timeout_sec="${3:-180}"
  local elapsed=0
  printf 'Waiting for %s (%s)...\n' "$name" "$url"
  while ! curl -sf "$url" >/dev/null 2>&1; do
    if [ "$elapsed" -ge "$timeout_sec" ]; then
      die "${name} did not become ready within ${timeout_sec}s. See ${LOG_DIR}/"
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done
  printf '%s is ready.\n' "$name"
}

kill_tree() {
  local pid="$1"
  [ -n "$pid" ] || return 0
  # Kill descendants first, then the root (gradle/npm wrappers spawn JVMs / node).
  local kids
  kids="$(pgrep -P "$pid" 2>/dev/null || true)"
  local k
  for k in $kids; do
    kill_tree "$k"
  done
  kill "$pid" 2>/dev/null || true
}

free_port() {
  local port="$1"
  local p
  if command -v lsof >/dev/null 2>&1; then
    for p in $(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true); do
      kill_tree "$p"
    done
  elif command -v fuser >/dev/null 2>&1; then
    fuser -k "${port}/tcp" 2>/dev/null || true
  fi
}

cleanup() {
  local ec=$?
  trap - EXIT INT TERM
  printf '\nStopping Backend and Frontend...\n' >&2
  kill_tree "${FRONTEND_PID:-}"
  kill_tree "${BACKEND_PID:-}"
  free_port 8080
  free_port 3333
  # Brief wait so listeners release before the script exits.
  sleep 1
  wait "${FRONTEND_PID:-}" 2>/dev/null || true
  wait "${BACKEND_PID:-}" 2>/dev/null || true
  exit "$ec"
}

trap cleanup EXIT INT TERM

mkdir -p "$LOG_DIR"

resolved_home="$(resolve_java21)" || {
  print_java_install_help
  exit 1
}
export JAVA_HOME="$resolved_home"
export PATH="${JAVA_HOME}/bin:${PATH}"
printf 'Using Java 21 at JAVA_HOME=%s\n' "$JAVA_HOME"
java -version

ensure_node

if [ ! -f "${ROOT_DIR}/Frontend/.env.local" ]; then
  cp "${ROOT_DIR}/Frontend/.env.sample" "${ROOT_DIR}/Frontend/.env.local"
  printf 'Created Frontend/.env.local from .env.sample (defaults: localhost:8080).\n'
fi

if needs_npm_ci; then
  printf 'Running npm ci in Frontend/...\n'
  (cd "${ROOT_DIR}/Frontend" && npm ci)
else
  printf 'Frontend/node_modules is present and up to date; skipping npm ci.\n'
fi

printf 'Starting Backend (./gradlew bootRun)...\n'
(
  cd "${ROOT_DIR}/Backend"
  ./gradlew bootRun --no-daemon
) >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

printf 'Starting Frontend (npm run dev)...\n'
(
  cd "${ROOT_DIR}/Frontend"
  npm run dev
) >"$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!

wait_for_url "http://localhost:8080/docs.html" "Backend" 180
wait_for_url "http://localhost:3333/" "Frontend" 120

cat <<EOF

Horus is ready.

  Open:  http://localhost:3333
  Then:  click "START WITH SAMPLE (サンプルで始める)"

Logs: ${LOG_DIR}/
Press Ctrl-C to stop Backend and Frontend.

EOF

# Wait until either process exits (or Ctrl-C).
wait "$BACKEND_PID" "$FRONTEND_PID"
