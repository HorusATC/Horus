export type EmergencySquawkCode = "7500" | "7600" | "7700";

export const EMERGENCY_SQUAWKS = {
  "7500": { label: "HIJK", color: "#cf2222" },
  "7600": { label: "RDOF", color: "#ea580c" },
  "7700": { label: "EMRG", color: "#cf2222" },
} as const satisfies Record<EmergencySquawkCode, { label: string; color: string }>;

export function isEmergencySquawk(
  squawk: string | null
): squawk is EmergencySquawkCode {
  return squawk === "7500" || squawk === "7600" || squawk === "7700";
}
