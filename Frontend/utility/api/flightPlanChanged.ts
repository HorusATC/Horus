export const FLIGHT_PLAN_CHANGED_EVENT = "horus:flight-plan-changed";

export type FlightPlanChangedDetail = {
  callsign: string;
};

export function notifyFlightPlanChanged(callsign: string): void {
  if (typeof window === "undefined" || !callsign) return;
  window.dispatchEvent(
    new CustomEvent<FlightPlanChangedDetail>(FLIGHT_PLAN_CHANGED_EVENT, {
      detail: { callsign },
    })
  );
}
