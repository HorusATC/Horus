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
} as const satisfies Record<keyof AtisState, true>;

export interface UpdateAtisRequest {
  runwayInUse: string;
  informationIdent?: string;
  wind?: AtisWindUpdate | null;
  qnhHpa?: number | null;
}

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

export async function updateAtis(
  airportIcao: string,
  request: UpdateAtisRequest
): Promise<{ ok: true; state: AtisState } | { ok: false; message: string }> {
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
    if (!response.ok) {
      return {
        ok: false,
        message: body.message ?? `ATIS update failed (${response.status})`,
      };
    }
    return { ok: true, state: body };
  } catch {
    return { ok: false, message: "ATIS update failed" };
  }
}
