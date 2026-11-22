import path from "node:path";

const repoRoot = process.cwd();

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function prettierWrite(filenames) {
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  return `npx prettier --write ${files}`;
}

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function eslintFix(filenames) {
  const rel = filenames.map((f) =>
    JSON.stringify(path.relative(path.join(repoRoot, "Frontend"), f))
  );
  return `cd Frontend && npx eslint --fix ${rel.join(" ")}`;
}

/**
 * Spotless Gradle `-PspotlessFiles` is a regex over absolute paths; running
 * module-level apply on any staged .java keeps the hook correct and fast enough.
 * @returns {string}
 */
function spotlessApply() {
  return "cd Backend && ./gradlew spotlessApply --no-daemon";
}

export default {
  "Frontend/**/*.{ts,tsx,js,jsx,json,css,md}": prettierWrite,
  "Frontend/**/*.{ts,tsx}": eslintFix,
  "Backend/**/*.java": spotlessApply,
};
