#!/usr/bin/env bash
# Point this clone at repo-root .husky and ensure root hook tooling exists.
# Safe to run from Frontend prepare (cwd may be Frontend/) or root prepare.
set -euo pipefail

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  exit 0
fi

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

git config core.hooksPath .husky
chmod +x .husky/pre-commit .husky/pre-push 2>/dev/null || true

# lint-staged / husky live in the root package.json
if [[ ! -d node_modules/lint-staged || ! -d node_modules/husky ]]; then
  npm ci --ignore-scripts
fi

if [[ -x node_modules/.bin/husky ]]; then
  # husky ensures .husky layout; hooksPath already set above
  npx husky || true
fi

git config core.hooksPath .husky
