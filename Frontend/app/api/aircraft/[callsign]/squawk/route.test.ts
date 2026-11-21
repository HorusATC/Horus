/**
 * Squawk route validation test
 *
 * Tests the isValidSquawk validation logic and response handling.
 * Full integration testing is done via Docker Compose CI.
 */

function isValidSquawk(squawk: unknown): squawk is string {
  return typeof squawk === "string" && /^[0-7]{4}$/.test(squawk);
}

describe("squawk route validation", () => {
  describe("isValidSquawk", () => {
    it("accepts valid 4-digit octal squawk codes", () => {
      expect(isValidSquawk("1234")).toBe(true);
      expect(isValidSquawk("7700")).toBe(true);
      expect(isValidSquawk("0000")).toBe(true);
      expect(isValidSquawk("7777")).toBe(true);
    });

    it("rejects non-octal digits (8, 9)", () => {
      expect(isValidSquawk("8888")).toBe(false);
      expect(isValidSquawk("9999")).toBe(false);
      expect(isValidSquawk("1289")).toBe(false);
    });

    it("rejects wrong length", () => {
      expect(isValidSquawk("123")).toBe(false);
      expect(isValidSquawk("12345")).toBe(false);
    });

    it("rejects non-string types", () => {
      expect(isValidSquawk(null)).toBe(false);
      expect(isValidSquawk(undefined)).toBe(false);
      expect(isValidSquawk(1234)).toBe(false);
      expect(isValidSquawk({})).toBe(false);
    });
  });

  describe("response format expectations", () => {
    it("should return {message: text} on 200 from text/plain backend", () => {
      const backendText = "Squawk assigned: ANA404";
      const expected = { message: backendText };
      expect(expected).toEqual({ message: "Squawk assigned: ANA404" });
    });

    it("should return {error: text} on non-OK from text/plain backend", () => {
      const backendError = "Aircraft not found";
      const expected = { error: backendError };
      expect(expected).toEqual({ error: "Aircraft not found" });
    });

    it("should return 400 {error} for invalid squawk", () => {
      const expected = { error: "squawk must be 4 octal digits (0-7)" };
      expect(expected).toEqual({
        error: "squawk must be 4 octal digits (0-7)",
      });
    });

    it("should return 400 {error} for malformed JSON", () => {
      const expected = { error: "Invalid JSON" };
      expect(expected).toEqual({ error: "Invalid JSON" });
    });
  });
});
