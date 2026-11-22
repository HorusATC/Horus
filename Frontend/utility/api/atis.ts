export interface AtisState {
  airportIcao: string;
  airportName: string;
  runwayInUse: string;
  informationIdent: string;
  text: string;
  availableRunways: string[];
}

export interface UpdateAtisRequest {
  runwayInUse: string;
  informationIdent?: string;
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
