#!/bin/sh
# Point this clone at repo-root .husky and ensure root hook tooling exists.
# Safe from Frontend prepare (cwd may be Frontend/) or root prepare.
# Always exits 0 when hooks cannot/should not be installed (Docker, CI image builds, missing git).
set -u

warn() {
  echo "warning: $*" >&2
}

# CI containers / non-interactive package installs must not fail npm ci on hooks.
if [ "${CI:-}" = "true" ] || [ "${CI:-}" = "1" ]; then
  echo "[install-git-hooks] CI=true — skipping hook install"
  exit 0
fi

if ! command -v git >/dev/null 2>&1; then
  echo "[install-git-hooks] git not found — skipping hook install"
  exit 0
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "[install-git-hooks] not a git work tree — skipping hook install"
  exit 0
fi

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "[install-git-hooks] cannot resolve git root — skipping"
  exit 0
}

cd "$ROOT" || exit 0

if [ ! -d .husky ]; then
  echo "[install-git-hooks] .husky missing — skipping"
  exit 0
fi

git config core.hooksPath .husky
chmod +x .husky/pre-commit .husky/pre-push 2>/dev/null || true

# lint-staged / husky live in the root package.json
if [ ! -d node_modules/lint-staged ] || [ ! -d node_modules/husky ]; then
  if [ -f package-lock.json ]; then
    npm ci --ignore-scripts || {
      warn "root npm ci --ignore-scripts failed; hooksPath is set but lint-staged may be missing"
      exit 0
    }
  else
    warn "root package-lock.json missing; cannot install hook tooling"
    exit 0
  fi
fi

if [ -x node_modules/.bin/husky ]; then
  if ! ./node_modules/.bin/husky; then
    warn "husky setup failed; core.hooksPath remains .husky — verify with: git config core.hooksPath"
  fi
else
  warn "husky binary missing after install; core.hooksPath set to .husky manually"
fi

git config core.hooksPath .husky
exit 0
