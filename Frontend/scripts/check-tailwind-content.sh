#!/bin/bash
set -e

# Check that all TypeScript/TSX files with Tailwind classes are covered by tailwind.config.ts content globs
# This prevents classes from being silently dropped from production CSS

echo "Checking Tailwind content coverage..."

# Find all TS/TSX files with potential Tailwind class strings
# Look for className= patterns (common in React)
FILES_WITH_CLASSES=$(find . -name "*.ts" -o -name "*.tsx" | \
  grep -v node_modules | \
  grep -v ".next" | \
  xargs grep -l 'className=' 2>/dev/null || true)

UNCOVERED_FILES=""

# Check if files are in covered directories (app, components, context, utility, pages)
for file in $FILES_WITH_CLASSES; do
  if ! echo "$file" | grep -qE '^\./app/|^\./components/|^\./context/|^\./utility/|^\./pages/'; then
    UNCOVERED_FILES="$UNCOVERED_FILES\n$file"
  fi
done

if [ -n "$UNCOVERED_FILES" ]; then
  echo "Error: Found files with Tailwind classes outside content globs:"
  echo -e "$UNCOVERED_FILES"
  echo ""
  echo "Add these directories to tailwind.config.ts content array"
  exit 1
fi

echo "✓ All files with Tailwind classes are covered by content globs"
exit 0
