export type ProcedureType = "SID" | "STAR" | "SID_TRANSITION";

export interface ProcedureSummary {
  id: string;
  type: ProcedureType;
  name: string;
  family: string;
  variant: string;
  airportIcao: string;
  rnav: boolean;
  assignable: boolean;
  applicableRunways: string[];
  enrouteTransitions: string[];
  legCount: number;
}

export interface ProcedureListResponse {
  sourceNotice: string;
  procedures: ProcedureSummary[];
}

export interface AssignProcedureRequest {
  procedureId: string;
  transitionId?: string;
  cruiseAltitude?: number;
  cruiseSpeed?: number;
}

export interface AssignProcedureResponse {
  success: boolean;
  callsign: string;
  procedureId: string;
  transitionId?: string;
  waypoints: string[];
  navigationMode: string;
  procedureVaActive: boolean;
  procedureVaCourseTrueDeg?: number;
  procedureVaAltitudeFt?: number;
  message?: string;
}

export async function fetchProcedures(
  airportIcao: string = "RJTT"
): Promise<ProcedureListResponse | null> {
  try {
    const response = await fetch(
      `/api/procedures?airport=${encodeURIComponent(airportIcao)}`,
      {
        method: "GET",
        headers: { Accept: "application/json" },
        cache: "no-store",
      }
    );
    if (!response.ok) return null;
    return (await response.json()) as ProcedureListResponse;
  } catch (error) {
    console.error("Error fetching procedures:", error);
    return null;
  }
}

export async function assignProcedure(
  callsign: string,
  request: AssignProcedureRequest
): Promise<AssignProcedureResponse | null> {
  try {
    const response = await fetch(
      `/api/aircraft/${encodeURIComponent(callsign)}/assign-procedure`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request),
      }
    );
    if (!response.ok) {
      return null;
    }
    return (await response.json()) as AssignProcedureResponse;
  } catch (error) {
    console.error("Error assigning procedure:", error);
    return null;
  }
}
