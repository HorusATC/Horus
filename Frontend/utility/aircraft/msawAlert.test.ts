import { describe, it, expect } from "@jest/globals";
import {
  MSAW_ALERTS,
  MSAW_PHRASEOLOGY,
  isMsawAlertType,
  parseMsawAlert,
  type MsawAlertType,
} from "./msawAlert";

describe("isMsawAlertType", () => {
  it("accepts MSA and LOW", () => {
    expect(isMsawAlertType("MSA")).toBe(true);
    expect(isMsawAlertType("LOW")).toBe(true);
  });

  it("rejects unrelated labels including EMRG", () => {
    expect(isMsawAlertType("EMRG")).toBe(false);
    expect(isMsawAlertType("LA")).toBe(false);
    expect(isMsawAlertType(null)).toBe(false);
    expect(isMsawAlertType(undefined)).toBe(false);
  });
});

describe("MSAW_ALERTS", () => {
  it("uses product labels MSA/LOW", () => {
    expect(MSAW_ALERTS.MSA.label).toBe("MSA");
    expect(MSAW_ALERTS.LOW.label).toBe("LOW");
  });

  it("uses amber distinct from EMRG red", () => {
    expect(MSAW_ALERTS.MSA.color).toBe("#f59e0b");
    expect(MSAW_ALERTS.LOW.color).toBe("#f59e0b");
    expect(MSAW_ALERTS.MSA.color).not.toBe("#cf2222");
  });

  it("covers every MsawAlertType via satisfies Record", () => {
    const types: MsawAlertType[] = ["MSA", "LOW"];
    types.forEach((type) => {
      expect(MSAW_ALERTS[type].label).toBeTruthy();
      expect(MSAW_ALERTS[type].color).toBeTruthy();
    });
  });
});

describe("MSAW_PHRASEOLOGY", () => {
  it("exposes Japanese and English phraseology from product code", () => {
    expect(MSAW_PHRASEOLOGY.ja).toContain("低高度警報");
    expect(MSAW_PHRASEOLOGY.en).toContain("LOW ALTITUDE WARNING");
  });
});

describe("parseMsawAlert", () => {
  it("parses a valid DTO", () => {
    expect(
      parseMsawAlert({ type: "MSA", msaFt: 2500, trigger: "CURRENT" })
    ).toEqual({ type: "MSA", msaFt: 2500, trigger: "CURRENT" });
  });

  it("treats missing or invalid payloads as no alert", () => {
    expect(parseMsawAlert(null)).toBeNull();
    expect(parseMsawAlert(undefined)).toBeNull();
    expect(
      parseMsawAlert({ type: "EMRG", msaFt: 2500, trigger: "CURRENT" })
    ).toBeNull();
    expect(
      parseMsawAlert({ type: "MSA", msaFt: NaN, trigger: "CURRENT" })
    ).toBeNull();
    expect(
      parseMsawAlert({ type: "MSA", msaFt: 2500, trigger: "LATER" })
    ).toBeNull();
  });
});
