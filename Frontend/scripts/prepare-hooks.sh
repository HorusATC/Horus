#!/bin/sh
# Frontend package prepare entry: never fail npm ci when hooks cannot be installed.
set -u

SCRIPT="../scripts/install-git-hooks.sh"
if [ ! -f "$SCRIPT" ]; then
  echo "[prepare] skip git hooks (../scripts/install-git-hooks.sh not present in this install context)"
  exit 0
fi

# install-git-hooks.sh is written to exit 0 on all skip paths
sh "$SCRIPT"
exit 0
