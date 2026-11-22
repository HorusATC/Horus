import { describe, it, expect } from "@jest/globals";
import fs from "fs";
import path from "path";
import {
  destinationPoint,
  selectClosestCenterlineRunway,
  type RunwayApproachGeometry,
} from "./rangeBearing";

/**
 * Inverted geometry repro (⑦ / PR-D): on each parallel centerline the closest-
 * centerline rule must select that runway itself (not the neighbor by shorter
 * along-track). Cross-track of the selection is near zero.
 */

const RJTT_PARALLEL: RunwayApproachGeometry[] = [
  {
    airportIcao: "RJTT",
    designator: "16R",
    thresholdLatitude: 35.55624167,
    thresholdLongitude: 139.77171944,
    approachCourseHeading: 149.88,
    usableForLanding: true,
  },
  {
    airportIcao: "RJTT",
    designator: "34L",
    thresholdLatitude: 35.5366,
    thresholdLongitude: 139.78566944,
    approachCourseHeading: 329.88,
    usableForLanding: true,
  },
  {
    airportIcao: "RJTT",
    designator: "16L",
    thresholdLatitude: 35.56285278,
    thresholdLongitude: 139.78870556,
    approachCourseHeading: 149.88,
    usableForLanding: true,
  },
  {
    airportIcao: "RJTT",
    designator: "34R",
    thresholdLatitude: 35.54250556,
    thresholdLongitude: 139.80315,
    approachCourseHeading: 329.88,
    usableForLanding: true,
  },
];

function thresholdOf(designator: string): RunwayApproachGeometry {
  const rwy = RJTT_PARALLEL.find((r) => r.designator === designator);
  if (!rwy) {
    throw new Error(`missing ${designator}`);
  }
  return rwy;
}

describe("geometry repro (inverted PR-D / ⑦)", () => {
  it.each(["16L", "16R", "34L", "34R"] as const)(
    "%s centerline at 5 NM selects self with near-zero cross-track",
    (designator) => {
      const rwy = thresholdOf(designator);
      const outbound = (rwy.approachCourseHeading + 180) % 360;
      const point = destinationPoint(
        {
          latitude: rwy.thresholdLatitude,
          longitude: rwy.thresholdLongitude,
        },
        outbound,
        5
      );
      const selected = selectClosestCenterlineRunway(point, RJTT_PARALLEL);
      expect(selected).not.toBeNull();
      expect(selected!.designator).toBe(designator);
      expect(Math.abs(selected!.crossTrackNm)).toBeLessThan(0.05);
      expect(selected!.alongTrackNm).toBeCloseTo(5.0, 2);
    }
  );

  it("shared golden table: each centerline × 1/5/9 NM selects self", () => {
    const goldenPath = path.join(
      __dirname,
      "../../testdata/closest-centerline-golden.json"
    );
    const golden = JSON.parse(fs.readFileSync(goldenPath, "utf8")) as {
      cases: Array<{
        centerlineDesignator: string;
        alongTrackNm: number;
        expectedDesignator: string;
      }>;
    };
    expect(golden.cases.length).toBe(12);
    for (const c of golden.cases) {
      const rwy = thresholdOf(c.centerlineDesignator);
      const outbound = (rwy.approachCourseHeading + 180) % 360;
      const point = destinationPoint(
        {
          latitude: rwy.thresholdLatitude,
          longitude: rwy.thresholdLongitude,
        },
        outbound,
        c.alongTrackNm
      );
      const selected = selectClosestCenterlineRunway(point, RJTT_PARALLEL);
      expect(selected?.designator).toBe(c.expectedDesignator);
      expect(Math.abs(selected!.crossTrackNm)).toBeLessThan(0.05);
    }
  });
});
