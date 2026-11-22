export const WIND_SPEED_KT_LIMITS = { min: 0, max: 99 } as const;
export const WIND_DIRECTION_DEG_LIMITS = { min: 1, max: 360 } as const;
export const QNH_HPA_LIMITS = { min: 850, max: 1100 } as const;

export type AtisWindUpdate = {
  directionDeg?: number;
  speedKt: number;
};

export interface AtisState {
  airportIcao: string;
  airportName: string;
  runwayInUse: string;
  informationIdent: string;
  text: string;
  availableRunways: string[];
  windAvailable: boolean;
  windDirectionDeg?: number;
  windSpeedKt?: number;
  qnhAvailable: boolean;
  qnhHpa?: number;
  revision: number;
}

export const ATIS_STATE_FIELD_KEYS = {
  airportIcao: true,
  airportName: true,
  runwayInUse: true,
  informationIdent: true,
  text: true,
  availableRunways: true,
  windAvailable: true,
  windDirectionDeg: true,
  windSpeedKt: true,
  qnhAvailable: true,
  qnhHpa: true,
  revision: true,
} as const satisfies Record<keyof AtisState, true>;

export interface UpdateAtisRequest {
  runwayInUse: string;
  informationIdent?: string;
  wind?: AtisWindUpdate | null;
  qnhHpa?: number | null;
  expectedRevision?: number;
}

export type ParseStrictIntResult =
  | { ok: true; value: number }
  | { ok: false; message: string };

/** Digits-only integer parser shared by wind and QNH; rejects decimals without truncating. */
export function parseStrictNonNegativeInt(
  text: string,
  min: number,
  max: number,
  rangeMessage: string
): ParseStrictIntResult {
  const trimmed = text.trim();
  if (!/^\d+$/.test(trimmed)) {
    return { ok: false, message: rangeMessage };
  }
  const value = Number(trimmed);
  if (value < min || value > max) {
    return { ok: false, message: rangeMessage };
  }
  return { ok: true, value };
}

export type UpdateAtisResult =
  | { ok: true; state: AtisState }
  | { ok: false; conflict: true; state: AtisState; message: string }
  | { ok: false; conflict?: false; message: string };

async function fetchJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(path, {
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

export async function fetchAtisAll(): Promise<AtisState[] | null> {
  return fetchJson<AtisState[]>("/api/atis");
}

function asAtisState(
  body: AtisState & { message?: string; success?: boolean }
): AtisState {
  return {
    airportIcao: body.airportIcao,
    airportName: body.airportName,
    runwayInUse: body.runwayInUse,
    informationIdent: body.informationIdent,
    text: body.text,
    availableRunways: body.availableRunways,
    windAvailable: body.windAvailable,
    windDirectionDeg: body.windDirectionDeg,
    windSpeedKt: body.windSpeedKt,
    qnhAvailable: body.qnhAvailable,
    qnhHpa: body.qnhHpa,
    revision: body.revision,
  };
}

export async function updateAtis(
  airportIcao: string,
  request: UpdateAtisRequest
): Promise<UpdateAtisResult> {
  try {
    const response = await fetch(
      `/api/atis/${encodeURIComponent(airportIcao)}`,
      {
        method: "PUT",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
        cache: "no-store",
      }
    );
    const body = (await response.json()) as AtisState & {
      message?: string;
      success?: boolean;
    };
    if (response.status === 409) {
      return {
        ok: false,
        conflict: true,
        state: asAtisState(body),
        message: body.message ?? "ATIS changed by another controller",
      };
    }
    if (!response.ok) {
      return {
        ok: false,
        message: body.message ?? `ATIS update failed (${response.status})`,
      };
    }
    return { ok: true, state: asAtisState(body) };
  } catch {
    return { ok: false, message: "ATIS update failed" };
  }
}
