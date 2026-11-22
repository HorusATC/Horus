import {
  defaultHoldLegMinutes,
  HOLD_STANDARD_LEG_ALTITUDE_FT,
  HOLD_STANDARD_LEG_MINUTES_ABOVE_14000_FT,
  HOLD_STANDARD_LEG_MINUTES_AT_OR_BELOW_14000_FT,
} from "./flightPlan";

describe("defaultHoldLegMinutes", () => {
  it("uses 1.0 min at or below 14,000 ft", () => {
    expect(defaultHoldLegMinutes(HOLD_STANDARD_LEG_ALTITUDE_FT)).toBe(
      HOLD_STANDARD_LEG_MINUTES_AT_OR_BELOW_14000_FT
    );
    expect(defaultHoldLegMinutes(5000)).toBe(
      HOLD_STANDARD_LEG_MINUTES_AT_OR_BELOW_14000_FT
    );
  });

  it("uses 1.5 min above 14,000 ft", () => {
    expect(defaultHoldLegMinutes(HOLD_STANDARD_LEG_ALTITUDE_FT + 1)).toBe(
      HOLD_STANDARD_LEG_MINUTES_ABOVE_14000_FT
    );
  });
});
