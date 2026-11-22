import path from "node:path";

const repoRoot = process.cwd();

/**
 * @param {string[]} filenames
 * @returns {string}
 */
function eslintFix(filenames) {
  const files = filenames.map((f) => JSON.stringify(f)).join(" ");
  const pluginsRoot = JSON.stringify(path.join(repoRoot, "Frontend"));
  // Absolute paths + resolve-plugins-relative-to avoids needing a shell `cd`
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
  // lint-staged appends staged file paths to this command
  "Frontend/**/*.{ts,tsx,js,jsx,json,css,md}": "prettier --write",
  "Frontend/**/*.{ts,tsx}": eslintFix,
  "Backend/**/*.java": spotlessApply,
};
