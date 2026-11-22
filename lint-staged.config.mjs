import path from "node:path";

const repoRoot = process.cwd();

/**
 * @param {string[]} filenames
 * @returns {string[]}
 */
function prettierWrite(filenames) {
  if (filenames.length === 0) return [];
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  return [`node scripts/run-frontend-bin.mjs prettier --write ${files}`];
}

/**
 * @param {string[]} filenames
 * @returns {string[]}
 */
function eslintFix(filenames) {
  if (filenames.length === 0) return [];
  const files = filenames.map((f) => JSON.stringify(path.resolve(f))).join(" ");
  return [`node scripts/run-frontend-bin.mjs eslint --fix ${files}`];
}

/**
 * @param {string[]} filenames
 * @returns {string[]}
 */
function spotlessApply(filenames) {
  if (filenames.length === 0) return [];
  const abs = filenames
    .map((f) => JSON.stringify(path.resolve(repoRoot, f)))
    .join(" ");
  return [`sh scripts/spotless-staged.sh ${abs}`];
}

export default {
  "Frontend/**/*.{ts,tsx,js,jsx,json,css,md}": prettierWrite,
  "Frontend/**/*.{ts,tsx}": eslintFix,
  "Backend/**/*.java": spotlessApply,
};
