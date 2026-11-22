#!/bin/sh
# Apply Spotless only to the given absolute file paths (Spotless 6.x IdeHook).
# Usage: spotless-staged.sh /abs/path/A.java [/abs/path/B.java ...]
set -eu

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <absolute-java-path>..." >&2
  exit 2
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

GRADLEW="./Backend/gradlew"
if [ ! -x "$GRADLEW" ]; then
  echo "error: missing $GRADLEW" >&2
  exit 1
fi

for f in "$@"; do
  case "$f" in
    /*) ;;
    *)
      echo "error: spotlessIdeHook requires an absolute path, got: $f" >&2
      exit 1
      ;;
  esac
  if [ ! -f "$f" ]; then
    echo "error: file not found: $f" >&2
    exit 1
  fi
  # One IdeHook invocation per file (Spotless 6.25 supports a single path).
  "$GRADLEW" -p Backend spotlessApply -PspotlessIdeHook="$f" --no-daemon
done
