export type EmergencySquawkCode = "7500" | "7600" | "7700";

export const EMERGENCY_SQUAWKS = {
  "7500": {
    label: "HIJK",
    color: "#cf2222",
    buttonClass: "bg-red-600 hover:bg-red-700",
  },
  "7600": {
    label: "RDOF",
    color: "#ea580c",
    buttonClass: "bg-orange-600 hover:bg-orange-700",
  },
  "7700": {
    label: "EMRG",
    color: "#cf2222",
    buttonClass: "bg-red-600 hover:bg-red-700",
  },
} as const satisfies Record<
  EmergencySquawkCode,
  { label: string; color: string; buttonClass: string }
>;

export function isEmergencySquawk(
  squawk: string | null
): squawk is EmergencySquawkCode {
  return squawk === "7500" || squawk === "7600" || squawk === "7700";
}
