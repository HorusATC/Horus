import { describe, it, expect } from "@jest/globals";

function getEmergencySquawk(squawk: string | null): string | null {
  if (squawk === "7500" || squawk === "7600" || squawk === "7700") {
    return squawk;
  }
  return null;
}

describe("Emergency Squawk Detection", () => {
  it("should detect 7500 as emergency", () => {
    expect(getEmergencySquawk("7500")).toBe("7500");
  });

  it("should detect 7600 as emergency", () => {
    expect(getEmergencySquawk("7600")).toBe("7600");
  });

  it("should detect 7700 as emergency", () => {
    expect(getEmergencySquawk("7700")).toBe("7700");
  });

  it("should not detect 1200 as emergency", () => {
    expect(getEmergencySquawk("1200")).toBeNull();
  });

  it("should not detect 0000 as emergency", () => {
    expect(getEmergencySquawk("0000")).toBeNull();
  });

  it("should handle null squawk", () => {
    expect(getEmergencySquawk(null)).toBeNull();
  });

  it("should not detect 7501 as emergency", () => {
    expect(getEmergencySquawk("7501")).toBeNull();
  });
});

describe("Emergency Squawk Label", () => {
  function getEmergencyLabel(squawk: string): string {
    if (squawk === "7500") return "HIJK";
    if (squawk === "7600") return "RDOF";
    if (squawk === "7700") return "EMRG";
    return "";
  }

  it("should return HIJK for 7500", () => {
    expect(getEmergencyLabel("7500")).toBe("HIJK");
  });

  it("should return RDOF for 7600", () => {
    expect(getEmergencyLabel("7600")).toBe("RDOF");
  });

  it("should return EMRG for 7700", () => {
    expect(getEmergencyLabel("7700")).toBe("EMRG");
  });
});
