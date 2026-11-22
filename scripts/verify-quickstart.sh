#!/usr/bin/env bash
# Mechanical checks for local quickstart success criteria.
#
# Usage:
#   scripts/verify-quickstart.sh --readme-only   # check 1 (CI-safe, no servers)
#   scripts/verify-quickstart.sh --live          # checks 2–4 (servers must be up)
#   scripts/verify-quickstart.sh --all           # checks 1–4
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:---all}"
BASE_URL="${QUICKSTART_BASE_URL:-http://localhost:3333}"
SAMPLE_JSON="${ROOT_DIR}/Frontend/data/quickstart-arrival-sample.json"
DEPARTURE_SAMPLE_JSON="${ROOT_DIR}/Frontend/data/quickstart-departure-sample.json"

die() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'OK: %s\n' "$*"
}

check_readme() {
  local readme="${ROOT_DIR}/README.md"
  [ -f "$readme" ] || die "README.md not found"

  local head80
  head80="$(head -n 80 "$readme")"

  printf '%s' "$head80" | grep -q 'dev-up' \
    || die "README first 80 lines must mention dev-up"

  printf '%s' "$head80" | grep -q 'START WITH SAMPLE' \
    || die "README first 80 lines must mention START WITH SAMPLE"

  printf '%s' "$head80" | grep -qE 'VAMOS|quickstart-departure-sample' \
    || die "README first 80 lines must mention VAMOS departure sample / quickstart-departure-sample"

  # Reject the outdated "spawn via Swagger / docs.html" guidance in the head.
  if printf '%s' "$head80" | grep -qiE 'docs\.html.*(spawn|航空機|機体)|(spawn|航空機|機体).*(Swagger|docs\.html)'; then
    die "README first 80 lines must not instruct creating aircraft via docs.html / Swagger"
  fi
  if printf '%s' "$head80" | grep -q 'フライトプラン作成・航空機 spawn は現状 Swagger'; then
    die "README still contains outdated Swagger spawn wording"
  fi

  pass "README quickstart contract (check 1)"
}

json_len() {
  # Prefer jq; fall back to node.
  local body="$1"
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$body" | jq 'length'
  else
    printf '%s' "$body" | node -e 'let s="";process.stdin.on("data",d=>s+=d);process.stdin.on("end",()=>{const j=JSON.parse(s);console.log(Array.isArray(j)?j.length:0)})'
  fi
}

lat_lon_fingerprint() {
  local body="$1"
  # AircraftLocationDto nests coords under .position
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$body" | jq -c '[.[] | {c: .callsign, lat: .position.latitude, lon: .position.longitude}] | sort_by(.c)'
  else
    printf '%s' "$body" | node -e '
      let s="";process.stdin.on("data",d=>s+=d);process.stdin.on("end",()=>{
        const j=JSON.parse(s);
        const rows=j.map(a=>({c:a.callsign,lat:a.position.latitude,lon:a.position.longitude}))
          .sort((a,b)=>String(a.c).localeCompare(String(b.c)));
        console.log(JSON.stringify(rows));
      })'
  fi
}

check_live() {
  [ -f "$SAMPLE_JSON" ] || die "Sample JSON missing: $SAMPLE_JSON"

  local home_body home_code
  home_code="$(curl -s -o /tmp/horus-home.html -w '%{http_code}' "${BASE_URL}/")"
  [ "$home_code" = "200" ] || die "GET ${BASE_URL}/ expected 200, got ${home_code}"
  home_body="$(cat /tmp/horus-home.html)"
  printf '%s' "$home_body" | grep -q 'START WITH SAMPLE (サンプルで始める)' \
    || die "Homepage body must contain START WITH SAMPLE (サンプルで始める)"
  pass "Homepage 200 + START WITH SAMPLE (check 2)"

  local load_code
  load_code="$(curl -s -o /tmp/horus-load.json -w '%{http_code}' \
    -X POST -H 'Content-Type: application/json' \
    --data-binary @"$SAMPLE_JSON" \
    "${BASE_URL}/api/scenario/load")"
  [ "$load_code" = "200" ] || die "POST /api/scenario/load expected 200, got ${load_code}: $(cat /tmp/horus-load.json)"

  local start_code
  start_code="$(curl -s -o /tmp/horus-start.txt -w '%{http_code}' \
    -X POST "${BASE_URL}/api/simulation/start")"
  [ "$start_code" = "200" ] || die "POST /api/simulation/start expected 200, got ${start_code}"

  local loc1
  loc1="$(curl -sf "${BASE_URL}/api/aircraft/location/all")" \
    || die "GET /api/aircraft/location/all failed"
  local count
  count="$(json_len "$loc1")"
  [ "$count" = "5" ] || die "Expected 5 aircraft, got ${count}"
  pass "Scenario load + start + 5 aircraft (check 3)"

  sleep 2
  local loc2
  loc2="$(curl -sf "${BASE_URL}/api/aircraft/location/all")" \
    || die "Second GET /api/aircraft/location/all failed"
  local fp1 fp2
  fp1="$(lat_lon_fingerprint "$loc1")"
  fp2="$(lat_lon_fingerprint "$loc2")"
  [ "$fp1" != "$fp2" ] || die "Expected at least one aircraft lat/lon to change after 2s"
  pass "Aircraft positions moved (check 4)"

  [ -f "$DEPARTURE_SAMPLE_JSON" ] || die "Departure sample JSON missing: $DEPARTURE_SAMPLE_JSON"
  local dep_load_code
  dep_load_code="$(curl -s -o /tmp/horus-dep-load.json -w '%{http_code}' \
    -X POST -H 'Content-Type: application/json' \
    --data-binary @"$DEPARTURE_SAMPLE_JSON" \
    "${BASE_URL}/api/scenario/load")"
  [ "$dep_load_code" = "200" ] || die "POST departure /api/scenario/load expected 200, got ${dep_load_code}: $(cat /tmp/horus-dep-load.json)"

  local dep_start_code
  dep_start_code="$(curl -s -o /tmp/horus-dep-start.txt -w '%{http_code}' \
    -X POST "${BASE_URL}/api/simulation/start")"
  [ "$dep_start_code" = "200" ] || die "POST departure /api/simulation/start expected 200, got ${dep_start_code}"

  local dep_loc
  dep_loc="$(curl -sf "${BASE_URL}/api/aircraft/location/all")" \
    || die "GET /api/aircraft/location/all after departure sample failed"
  local dep_count
  dep_count="$(json_len "$dep_loc")"
  [ "$dep_count" = "1" ] || die "Expected 1 departure aircraft, got ${dep_count}"
  pass "VAMOS departure sample load + start + 1 aircraft (check 5)"
}

case "$MODE" in
  --readme-only)
    check_readme
    ;;
  --live)
    check_live
    ;;
  --all|"")
    check_readme
    check_live
    ;;
  *)
    die "Unknown mode: $MODE (use --readme-only | --live | --all)"
    ;;
esac

printf 'All requested quickstart checks passed.\n'
