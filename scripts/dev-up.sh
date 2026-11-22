#!/usr/bin/env bash
# Start Backend (bootRun) and Frontend (npm run dev) for local quickstart.
# Requires Java 21 and Node.js 18+. Ctrl-C stops both processes.
# Works on macOS and Linux (setsid when available; otherwise job-control / PID trees).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.dev-logs"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
BACKEND_PID=""
FRONTEND_PID=""
# 1 when children were started with setsid (process-group kill is safe).
USE_SETSID=0

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
  local ver_line
  ver_line="$("$java_bin" -version 2>&1 | head -n 1)" || return 1
  if printf '%s' "$ver_line" | grep -Eq 'version "21([.\"]|$)'; then
    printf '%s\n' "21"
    return 0
  fi
  return 1
}

is_java21_home() {
  local home="$1"
  if [ -z "$home" ]; then
    return 1
  fi
  if [ ! -x "${home}/bin/java" ]; then
    return 1
  fi
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
    candidate="$(dirname "$(dirname "$(command -v java)")")"
    if is_java21_home "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
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

# Spec Decision: npm ci only when node_modules is missing or older than package-lock.json.
needs_npm_ci() {
  local fe="${ROOT_DIR}/Frontend"
  if [ ! -d "${fe}/node_modules" ]; then
    return 0
  fi
  if [ "${fe}/package-lock.json" -nt "${fe}/node_modules" ]; then
    return 0
  fi
  return 1
}

port_in_use() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | grep -qE ":${port}\\s"
    return $?
  fi
  # Fallback: try binding briefly via python (rare environments without lsof/ss).
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$port" <<'PY'
import socket, sys
port = int(sys.argv[1])
s = socket.socket()
try:
    s.bind(("127.0.0.1", port))
except OSError:
    sys.exit(0)  # in use
s.close()
sys.exit(1)  # free
PY
    return $?
  fi
  return 1
}

describe_port_holder() {
  local port="$1"
  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
  elif command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | grep -E ":${port}\\s" || true
  fi
}

assert_ports_free() {
  local port
  for port in 8080 3333; do
    if port_in_use "$port"; then
      printf 'Port %s is already in use. Stop the other process before running ./scripts/dev-up.sh.\n' "$port" >&2
      printf 'Find it with: lsof -iTCP:%s -sTCP:LISTEN\n' "$port" >&2
      describe_port_holder "$port" >&2
      exit 1
    fi
  done
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

# Kill a PID and all of its descendants (pkill -P recursion). Never touches unrelated listeners.
kill_tree() {
  local pid="$1"
  local sig="${2:-TERM}"
  [ -n "$pid" ] || return 0
  local kids
  kids="$(pgrep -P "$pid" 2>/dev/null || true)"
  local k
  for k in $kids; do
    kill_tree "$k" "$sig"
  done
  kill "-${sig}" "$pid" 2>/dev/null || true
}

start_backend() {
  if command -v setsid >/dev/null 2>&1; then
    USE_SETSID=1
    setsid bash -c "cd \"${ROOT_DIR}/Backend\" && exec ./gradlew bootRun --no-daemon" \
      >"$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
  else
    USE_SETSID=0
    # macOS / no setsid: enable monitor mode so the background job gets its own process group.
    set -m
    (
      cd "${ROOT_DIR}/Backend"
      exec ./gradlew bootRun --no-daemon
    ) >"$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
  fi
}

start_frontend() {
  if command -v setsid >/dev/null 2>&1; then
    USE_SETSID=1
    setsid bash -c "cd \"${ROOT_DIR}/Frontend\" && exec npm run dev" \
      >"$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID=$!
  else
    USE_SETSID=0
    set -m
    (
      cd "${ROOT_DIR}/Frontend"
      exec npm run dev
    ) >"$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID=$!
  fi
}

cleanup() {
  local ec=$?
  trap - EXIT INT TERM HUP
  if [ -n "${BACKEND_PID:-}" ] || [ -n "${FRONTEND_PID:-}" ]; then
    printf '\nStopping Backend and Frontend...\n' >&2
    # Only signal process groups / PID trees we started — never lsof-kill arbitrary port holders.
    if [ "$USE_SETSID" -eq 1 ]; then
      if [ -n "${FRONTEND_PID:-}" ]; then
        kill -TERM -"${FRONTEND_PID}" 2>/dev/null || true
      fi
      if [ -n "${BACKEND_PID:-}" ]; then
        kill -TERM -"${BACKEND_PID}" 2>/dev/null || true
      fi
    fi
    kill_tree "${FRONTEND_PID:-}" TERM
    kill_tree "${BACKEND_PID:-}" TERM
    sleep 0.5
    if [ "$USE_SETSID" -eq 1 ]; then
      if [ -n "${FRONTEND_PID:-}" ]; then
        kill -KILL -"${FRONTEND_PID}" 2>/dev/null || true
      fi
      if [ -n "${BACKEND_PID:-}" ]; then
        kill -KILL -"${BACKEND_PID}" 2>/dev/null || true
      fi
    fi
    kill_tree "${FRONTEND_PID:-}" KILL
    kill_tree "${BACKEND_PID:-}" KILL
  fi
  exit "$ec"
}

trap cleanup EXIT INT TERM HUP

mkdir -p "$LOG_DIR"

# Refuse to start if ports are already taken (do not steal foreign processes).
assert_ports_free

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
start_backend
printf 'Starting Frontend (npm run dev)...\n'
start_frontend
if [ "$USE_SETSID" -eq 1 ]; then
  printf 'Process launcher: setsid\n'
else
  printf 'Process launcher: job-control fallback (no setsid)\n'
fi

wait_for_url "http://localhost:8080/docs.html" "Backend" 180
wait_for_url "http://localhost:3333/" "Frontend" 120

cat <<EOF

Horus is ready.

  Open:  http://localhost:3333
  Then:  click "START WITH SAMPLE (サンプルで始める)"

Logs: ${LOG_DIR}/
Press Ctrl-C to stop Backend and Frontend.

EOF

while kill -0 "${BACKEND_PID}" 2>/dev/null && kill -0 "${FRONTEND_PID}" 2>/dev/null; do
  sleep 1
done
