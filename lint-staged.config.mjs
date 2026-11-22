import path from "node:path";

const repoRoot = process.cwd();

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function prettierWrite(filenames) {
  // Use Frontend's prettier binary so format matches `cd Frontend && npm run format:check`
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  return `npm exec --prefix Frontend -- prettier --write ${files}`;
}

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function eslintFix(filenames) {
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  const pluginsRoot = JSON.stringify(path.join(repoRoot, "Frontend"));
  return `npm exec --prefix Frontend -- eslint --fix --resolve-plugins-relative-to ${pluginsRoot} ${files}`;
}

/**
 * Module-level apply when any Backend .java is staged.
 * @returns {string}
 */
function spotlessApply() {
  return "./Backend/gradlew -p Backend spotlessApply --no-daemon";
}

export default {
  "Frontend/**/*.{ts,tsx,js,jsx,json,css,md}": prettierWrite,
  "Frontend/**/*.{ts,tsx}": eslintFix,
  "Backend/**/*.java": spotlessApply,
};
