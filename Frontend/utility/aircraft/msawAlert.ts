export type MsawAlertType = "MSA" | "LOW";
export type MsawTrigger = "CURRENT" | "PREDICTED";

export type MsawAlertDto = {
  type: MsawAlertType;
  msaFt: number;
  trigger: MsawTrigger;
};

export const MSAW_ALERTS = {
  MSA: {
    label: "MSA",
    color: "#f59e0b",
  },
  LOW: {
    label: "LOW",
    color: "#f59e0b",
  },
} as const satisfies Record<MsawAlertType, { label: string; color: string }>;

export const MSAW_PHRASEOLOGY = {
  ja: "低高度警報、直ちに高度を点検してください。",
  en: "LOW ALTITUDE WARNING, CHECK YOUR ALTITUDE IMMEDIATELY.",
} as const;

export function isMsawAlertType(
  value: string | null | undefined
): value is MsawAlertType {
  return value === "MSA" || value === "LOW";
}

export function parseMsawAlert(
  raw: { type?: string; msaFt?: number; trigger?: string } | null | undefined
): MsawAlertDto | null {
  if (raw == null || !isMsawAlertType(raw.type)) {
    return null;
  }
  if (typeof raw.msaFt !== "number" || !Number.isFinite(raw.msaFt)) {
    return null;
  }
  if (raw.trigger !== "CURRENT" && raw.trigger !== "PREDICTED") {
    return null;
  }
  return {
    type: raw.type,
    msaFt: raw.msaFt,
    trigger: raw.trigger,
  };
}
