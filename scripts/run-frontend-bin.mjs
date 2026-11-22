#!/usr/bin/env node
/**
 * Run a Frontend local binary without npx network installs.
 * Usage: node scripts/run-frontend-bin.mjs <bin> [args...]
 */
import { existsSync } from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const frontendRoot = path.join(repoRoot, "Frontend");
const [binName, ...args] = process.argv.slice(2);

if (!binName) {
  console.error("usage: run-frontend-bin.mjs <bin> [args...]");
  process.exit(2);
}

const bin = path.join(frontendRoot, "node_modules", ".bin", binName);
if (!existsSync(bin)) {
  console.error(
    `error: Frontend deps missing (${binName}) — run: cd Frontend && npm ci`
  );
  process.exit(1);
}

let cwd = repoRoot;
let finalArgs = args;

if (binName === "eslint" || binName === "prettier") {
  cwd = frontendRoot;
}

if (binName === "eslint") {
  finalArgs = args.map((a) =>
    a.startsWith("-") ? a : path.relative(frontendRoot, path.resolve(a))
  );
}

const result = spawnSync(bin, finalArgs, { cwd, stdio: "inherit" });
process.exit(result.status ?? 1);
