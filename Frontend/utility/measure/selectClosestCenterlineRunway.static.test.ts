import { describe, it, expect } from "@jest/globals";
import fs from "fs";
import path from "path";

/**
 * Static check: selectClosestCenterlineRunway is the only greppable runway
 * selection implementation; findThresholdDistance only delegates.
 */

const REPO_ROOT = path.resolve(__dirname, "../../..");

const ALLOWED_NAME_FILES = new Set([
  "Frontend/utility/measure/rangeBearing.ts",
  "Frontend/utility/measure/geometry.repro.test.ts",
  "Frontend/utility/measure/selectClosestCenterlineRunway.static.test.ts",
  "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/ClosestCenterlineRunway.java",
  "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawDetector.java",
  "Backend/src/test/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawOverlapReproTest.java",
  "Backend/src/test/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/SelectClosestCenterlineRunwayStaticTest.java",
  "spec/20261123-frontend-polling-runway/spec.md",
  "spec/20261122-runway-distance-rbl/spec.md",
  "Frontend/README.md",
  "Backend/README.md",
]);

const SKIP_DIR = new Set(["node_modules", ".git", "build", ".next", ".gradle"]);

function walkFiles(dir: string, out: string[]): void {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIR.has(entry.name)) {
      continue;
    }
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walkFiles(full, out);
      continue;
    }
    if (!/\.(ts|tsx|java|md)$/.test(entry.name)) {
      continue;
    }
    out.push(full);
  }
}

describe("selectClosestCenterlineRunway static check", () => {
  it("name hits only definition, callers, golden tests, and docs", () => {
    const files: string[] = [];
    walkFiles(REPO_ROOT, files);
    const hits = files
      .filter((f) =>
        fs.readFileSync(f, "utf8").includes("selectClosestCenterlineRunway")
      )
      .map((f) => path.relative(REPO_ROOT, f).split(path.sep).join("/"));

    expect(hits.length).toBeGreaterThanOrEqual(4);
    for (const file of hits) {
      expect(ALLOWED_NAME_FILES.has(file)).toBe(true);
    }
  });

  it("findThresholdDistance only delegates; no local selection loop", () => {
    const src = fs.readFileSync(
      path.join(REPO_ROOT, "Frontend/utility/measure/rangeBearing.ts"),
      "utf8"
    );
    const fn = src.match(/export function findThresholdDistance\([\s\S]*?\n\}/);
    expect(fn).not.toBeNull();
    expect(fn![0]).toContain("selectClosestCenterlineRunway");
    expect(fn![0]).not.toMatch(/for\s*\(/);
    expect(fn![0]).not.toMatch(/alongTrackNm\s*</);
  });

  it("MsawDetector.checkApproachCorridor only delegates", () => {
    const src = fs.readFileSync(
      path.join(
        REPO_ROOT,
        "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawDetector.java"
      ),
      "utf8"
    );
    const fn = src.match(
      /private ApproachCorridorCheck checkApproachCorridor\([\s\S]*?\n    \}/
    );
    expect(fn).not.toBeNull();
    expect(fn![0]).toContain("selectClosestCenterlineRunway");
    expect(fn![0]).not.toMatch(/for\s*\(/);
  });
});
