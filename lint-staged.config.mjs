import path from "node:path";

const repoRoot = process.cwd();

/**
 * lint-staged runs task strings without a shell by default, so use bash -lc
 * whenever the command needs cd / &&.
 * @param {string} command
 * @returns {string}
 */
function sh(command) {
  return `bash -lc ${JSON.stringify(command)}`;
}

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function prettierWrite(filenames) {
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  return sh(`npx prettier --write ${files}`);
}

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function eslintFix(filenames) {
  const rel = filenames
    .map((f) => JSON.stringify(path.relative(path.join(repoRoot, "Frontend"), f)))
    .join(" ");
  return sh(`cd Frontend && npx eslint --fix ${rel}`);
}

/**
 * Module-level apply when any Backend .java is staged (fast enough; avoids
 * fragile -PspotlessFiles regex over absolute paths).
 * @returns {string}
 */
function spotlessApply() {
  return sh("cd Backend && ./gradlew spotlessApply --no-daemon");
}

export default {
  "Frontend/**/*.{ts,tsx,js,jsx,json,css,md}": prettierWrite,
  "Frontend/**/*.{ts,tsx}": eslintFix,
  "Backend/**/*.java": spotlessApply,
};
