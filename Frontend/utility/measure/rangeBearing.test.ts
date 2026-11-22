import { describe, it, expect } from "@jest/globals";
import {
  destinationPoint,
  findThresholdDistance,
  formatDurationMss,
  formatRblLabel,
  formatThresholdReadout,
  formatTrueBearingDeg,
  haversineDistanceNm,
  horizontalCpaSeconds,
  initialBearingDeg,
  isInApproachCorridor,
  projectOntoApproachCorridor,
  timeToDistanceSeconds,
  type RunwayApproachGeometry,
} from "./rangeBearing";

const RJTT_16R_THRESHOLD = { latitude: 35.553, longitude: 139.781 };
const RJAA_16R_THRESHOLD = { latitude: 35.7647, longitude: 140.3864 };
const RJTT_APPROACH_COURSE = 163;

const RJTT_RUNWAY: RunwayApproachGeometry = {
  airportIcao: "RJTT",
  designator: "16R",
  thresholdLatitude: RJTT_16R_THRESHOLD.latitude,
  thresholdLongitude: RJTT_16R_THRESHOLD.longitude,
  approachCourseHeading: RJTT_APPROACH_COURSE,
};

describe("rangeBearing haversine / bearing (spec numeric cases)", () => {
  it("distance/bearing 1: 8 NM at 343° from RJTT 16R threshold", () => {
    const point = destinationPoint(RJTT_16R_THRESHOLD, 343, 8);
    expect(point.latitude).toBeCloseTo(35.68027007, 7);
    expect(point.longitude).toBeCloseTo(139.7330940835, 7);
    expect(haversineDistanceNm(RJTT_16R_THRESHOLD, point)).toBeCloseTo(8.0, 3);
    expect(initialBearingDeg(RJTT_16R_THRESHOLD, point)).toBeCloseTo(343.0, 2);
    expect(initialBearingDeg(point, RJTT_16R_THRESHOLD)).toBeCloseTo(
      162.972102,
      2
    );
  });

  it("distance/bearing 2: RJTT 16R threshold to RJAA 16R threshold", () => {
    expect(
      haversineDistanceNm(RJTT_16R_THRESHOLD, RJAA_16R_THRESHOLD)
    ).toBeCloseTo(32.1880525766, 3);
    expect(
      initialBearingDeg(RJTT_16R_THRESHOLD, RJAA_16R_THRESHOLD)
    ).toBeCloseTo(66.537584, 2);
    expect(
      initialBearingDeg(RJAA_16R_THRESHOLD, RJTT_16R_THRESHOLD)
    ).toBeCloseTo(246.89051, 2);
  });
});

describe("rangeBearing time / format", () => {
  it("matches arrival-time cases from the spec", () => {
    expect(timeToDistanceSeconds(8.0, 160)).toBeCloseTo(180.0, 1);
    expect(formatDurationMss(180)).toBe("3:00");
    expect(timeToDistanceSeconds(5.0, 180)).toBeCloseTo(100.0, 1);
    expect(formatDurationMss(100)).toBe("1:40");
    expect(timeToDistanceSeconds(10.0, 240)).toBeCloseTo(150.0, 1);
    expect(formatDurationMss(150)).toBe("2:30");
    expect(timeToDistanceSeconds(1.0, 140)).toBeCloseTo(25.714286, 1);
    expect(formatDurationMss(25.714286)).toBe("0:26");
    expect(timeToDistanceSeconds(8.0, 0)).toBeNull();
    expect(timeToDistanceSeconds(200.0, 100)).toBeNull();
  });

  it("formats threshold and RBL labels", () => {
    expect(formatThresholdReadout("16R", 8.2, 180)).toBe("16R 8.2NM 3:00");
    expect(formatThresholdReadout("16R", 8.2, null)).toBe("16R 8.2NM");
    expect(formatTrueBearingDeg(87)).toBe("087T");
    expect(formatRblLabel(12.4, 87, null)).toBe("12.4NM 087T");
    expect(formatRblLabel(12.4, 87, 100)).toBe("12.4NM 087T 1:40");
  });
});

describe("rangeBearing approach corridor", () => {
  it("includes along-track 8 NM / cross-track 0", () => {
    const point = destinationPoint(RJTT_16R_THRESHOLD, 343, 8);
    expect(
      isInApproachCorridor(point, RJTT_16R_THRESHOLD, RJTT_APPROACH_COURSE)
    ).toBe(true);
    const proj = projectOntoApproachCorridor(
      point,
      RJTT_16R_THRESHOLD,
      RJTT_APPROACH_COURSE
    );
    expect(proj.alongTrackNm).toBeCloseTo(8.0, 3);
    expect(Math.abs(proj.crossTrackNm)).toBeLessThan(0.001);
  });

  it("excludes along-track 8 NM / cross-track 2 NM", () => {
    // Point with along=8, cross=2 relative to outbound 343° from threshold.
    const bearingDiffDeg = (Math.atan2(2, 8) * 180) / Math.PI;
    const slantNm = Math.hypot(8, 2);
    const offset = destinationPoint(
      RJTT_16R_THRESHOLD,
      343 + bearingDiffDeg,
      slantNm
    );
    const proj = projectOntoApproachCorridor(
      offset,
      RJTT_16R_THRESHOLD,
      RJTT_APPROACH_COURSE
    );
    expect(proj.alongTrackNm).toBeCloseTo(8.0, 2);
    expect(Math.abs(proj.crossTrackNm)).toBeCloseTo(2.0, 2);
    expect(proj.inCorridor).toBe(false);
  });

  it("excludes points inside the threshold (departure side)", () => {
    const inside = destinationPoint(RJTT_16R_THRESHOLD, 163, 1);
    expect(
      isInApproachCorridor(inside, RJTT_16R_THRESHOLD, RJTT_APPROACH_COURSE)
    ).toBe(false);
  });

  it("includes along-track 10.0 NM exactly and excludes beyond", () => {
    const at10 = destinationPoint(RJTT_16R_THRESHOLD, 343, 10);
    expect(
      isInApproachCorridor(at10, RJTT_16R_THRESHOLD, RJTT_APPROACH_COURSE)
    ).toBe(true);
    const beyond = destinationPoint(RJTT_16R_THRESHOLD, 343, 10.01);
    expect(
      isInApproachCorridor(beyond, RJTT_16R_THRESHOLD, RJTT_APPROACH_COURSE)
    ).toBe(false);
  });

  it("includes cross-track 1.0 NM exactly", () => {
    const bearingDiffDeg = (Math.atan2(1, 8) * 180) / Math.PI;
    const slantNm = Math.hypot(8, 1);
    const atEdge = destinationPoint(
      RJTT_16R_THRESHOLD,
      343 + bearingDiffDeg,
      slantNm
    );
    const proj = projectOntoApproachCorridor(
      atEdge,
      RJTT_16R_THRESHOLD,
      RJTT_APPROACH_COURSE
    );
    expect(Math.abs(proj.crossTrackNm)).toBeCloseTo(1.0, 2);
    expect(proj.inCorridor).toBe(true);
  });

  it("findThresholdDistance returns designator and distance", () => {
    const point = destinationPoint(RJTT_16R_THRESHOLD, 343, 8);
    const result = findThresholdDistance(point, [RJTT_RUNWAY]);
    expect(result).not.toBeNull();
    expect(result!.designator).toBe("16R");
    expect(result!.alongTrackNm).toBeCloseTo(8.0, 3);
  });
});

describe("rangeBearing CPA", () => {
  it("head-on pair at 10 NM / 180 kt each closes in 100 s", () => {
    const aPos = RJTT_16R_THRESHOLD;
    const bPos = destinationPoint(aPos, 90, 10);
    expect(bPos.latitude).toBeCloseTo(35.552827373, 7);
    expect(bPos.longitude).toBeCloseTo(139.985490185, 7);
    expect(haversineDistanceNm(aPos, bPos)).toBeCloseTo(10.0, 3);
    expect(initialBearingDeg(aPos, bPos)).toBeCloseTo(90.0, 2);
    const cpa = horizontalCpaSeconds(
      aPos,
      { headingDeg: 90, groundSpeedKt: 180 },
      bPos,
      { headingDeg: 270, groundSpeedKt: 180 }
    );
    expect(cpa).not.toBeNull();
    expect(cpa!).toBeCloseTo(100, 1);
  });

  it("same headings yield no CPA time", () => {
    const aPos = RJTT_16R_THRESHOLD;
    const bPos = destinationPoint(aPos, 90, 10);
    expect(
      horizontalCpaSeconds(aPos, { headingDeg: 90, groundSpeedKt: 180 }, bPos, {
        headingDeg: 90,
        groundSpeedKt: 180,
      })
    ).toBeNull();
  });
});
