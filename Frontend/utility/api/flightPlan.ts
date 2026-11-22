export interface FlightPlanRouteStatusPoint {
  fix: string;
  latitude: number;
  longitude: number;
  altitude?: number;
  constraint?: string;
}

export interface FlightPlanStatus {
  callsign: string;
  navigationMode: string;
  currentWaypointIndex?: number;
  currentWaypoint?: string | null;
  remainingWaypoints?: string[];
  route?: FlightPlanRouteStatusPoint[];
  departureAirport?: string;
  arrivalAirport?: string;
  hasFlightPlan?: boolean;
  procedureVaActive?: boolean;
}

export type HoldTurnDirection = "LEFT" | "RIGHT";

export type HoldLegRequest =
  | { kind: "minutes"; legMinutes: number }
  | { kind: "nm"; legNm: number };

export interface HoldAtFixParams {
  fixName: string;
  turnDirection?: HoldTurnDirection;
  inboundCourseDeg?: number;
  leg?: HoldLegRequest;
}

export async function fetchFlightPlan(
  callsign: string
): Promise<FlightPlanStatus | null> {
  try {
    const response = await fetch(`/api/aircraft/${callsign}/flightplan`, {
      method: "GET",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) return null;
    return (await response.json()) as FlightPlanStatus;
  } catch (error) {
    console.error("Error fetching flight plan:", error);
    return null;
  }
}

export async function directToFix(
  callsign: string,
  fixName: string,
  resumeFlightPlan: boolean
): Promise<boolean> {
  try {
    const response = await fetch(`/api/aircraft/${callsign}/direct-to`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ fixName, resumeFlightPlan }),
    });
    return response.ok;
  } catch (error) {
    console.error("Error directing to fix:", error);
    return false;
  }
}

export async function resumeNavigation(callsign: string): Promise<boolean> {
  try {
    const response = await fetch(
      `/api/aircraft/${callsign}/resume-navigation`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      }
    );
    return response.ok;
  } catch (error) {
    console.error("Error resuming navigation:", error);
    return false;
  }
}

/** FAA AIP ENR 1.5 default timed-leg minutes by altitude (UI default only). */
export const HOLD_STANDARD_LEG_MINUTES_AT_OR_BELOW_14000_FT = 1.0;
export const HOLD_STANDARD_LEG_MINUTES_ABOVE_14000_FT = 1.5;
export const HOLD_STANDARD_LEG_ALTITUDE_FT = 14000;

export function defaultHoldLegMinutes(altitudeFt: number): number {
  return altitudeFt > HOLD_STANDARD_LEG_ALTITUDE_FT
    ? HOLD_STANDARD_LEG_MINUTES_ABOVE_14000_FT
    : HOLD_STANDARD_LEG_MINUTES_AT_OR_BELOW_14000_FT;
}

export async function holdAtFix(
  callsign: string,
  params: HoldAtFixParams
): Promise<boolean> {
  try {
    const body: Record<string, string | number> = {
      fixName: params.fixName,
      turnDirection: params.turnDirection ?? "RIGHT",
    };
    if (params.inboundCourseDeg != null) {
      body.inboundCourseDeg = params.inboundCourseDeg;
    }
    if (params.leg?.kind === "minutes") {
      body.legMinutes = params.leg.legMinutes;
    } else if (params.leg?.kind === "nm") {
      body.legNm = params.leg.legNm;
    }
    const response = await fetch(`/api/aircraft/${callsign}/hold`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return response.ok;
  } catch (error) {
    console.error("Error applying hold:", error);
    return false;
  }
}
