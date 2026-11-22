import {
  holdEntryProcedureLabel,
  HOLDING_PATTERN_DTO_FIELD_PARTITION,
  type HoldingPatternDto,
} from "./holdingPattern";

describe("holdEntryProcedureLabel", () => {
  it("maps AIM procedures to short radar labels", () => {
    expect(holdEntryProcedureLabel("DIRECT")).toBe("DIR");
    expect(holdEntryProcedureLabel("TEARDROP")).toBe("TDP");
    expect(holdEntryProcedureLabel("PARALLEL")).toBe("PAR");
    expect(holdEntryProcedureLabel(null)).toBeNull();
    expect(holdEntryProcedureLabel(undefined)).toBeNull();
  });
});

describe("HOLDING_PATTERN_DTO_FIELD_PARTITION", () => {
  it("includes entryProcedure as optional", () => {
    expect(HOLDING_PATTERN_DTO_FIELD_PARTITION.entryProcedure).toBe("optional");
    expect(HOLDING_PATTERN_DTO_FIELD_PARTITION.pattern).toBe("required");
    const sample: HoldingPatternDto = {
      fixName: "ABENO",
      fixLatitude: 35,
      fixLongitude: 139,
      inboundCourseDeg: 90,
      turnDirection: "RIGHT",
      outboundLegNm: 3,
      turnRateDegPerSec: 3,
      turnRadiusNm: 1,
      pattern: [
        { latitude: 35, longitude: 139 },
        { latitude: 35.01, longitude: 139 },
      ],
      entryProcedure: "DIRECT",
    };
    expect(sample.entryProcedure).toBe("DIRECT");
  });
});
