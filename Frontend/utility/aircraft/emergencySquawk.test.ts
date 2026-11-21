import { describe, it, expect } from "@jest/globals";
import {
  isEmergencySquawk,
  EMERGENCY_SQUAWKS,
  type EmergencySquawkCode,
} from "./emergencySquawk";

describe("isEmergencySquawk", () => {
  it("should return true for 7500", () => {
    expect(isEmergencySquawk("7500")).toBe(true);
  });

  it("should return true for 7600", () => {
    expect(isEmergencySquawk("7600")).toBe(true);
  });

  it("should return true for 7700", () => {
    expect(isEmergencySquawk("7700")).toBe(true);
  });

  it("should return false for 7501", () => {
    expect(isEmergencySquawk("7501")).toBe(false);
  });

  it("should return false for 1200", () => {
    expect(isEmergencySquawk("1200")).toBe(false);
  });

  it("should return false for null", () => {
    expect(isEmergencySquawk(null)).toBe(false);
  });

  it("should return false for empty string", () => {
    expect(isEmergencySquawk("")).toBe(false);
  });
});

describe("EMERGENCY_SQUAWKS", () => {
  it("should have label and color for 7500", () => {
    expect(EMERGENCY_SQUAWKS["7500"].label).toBe("HIJK");
    expect(EMERGENCY_SQUAWKS["7500"].color).toBeTruthy();
  });

  it("should have label and color for 7600", () => {
    expect(EMERGENCY_SQUAWKS["7600"].label).toBe("RDOF");
    expect(EMERGENCY_SQUAWKS["7600"].color).toBeTruthy();
  });

  it("should have label and color for 7700", () => {
    expect(EMERGENCY_SQUAWKS["7700"].label).toBe("EMRG");
    expect(EMERGENCY_SQUAWKS["7700"].color).toBeTruthy();
  });

  it("should have exactly 3 emergency codes", () => {
    const codes = Object.keys(EMERGENCY_SQUAWKS);
    expect(codes).toHaveLength(3);
  });

  it("should satisfy type exhaustiveness", () => {
    const codes: EmergencySquawkCode[] = ["7500", "7600", "7700"];
    codes.forEach((code) => {
      const config = EMERGENCY_SQUAWKS[code];
      expect(config.label).toBeTruthy();
      expect(config.color).toBeTruthy();
    });
  });
});
