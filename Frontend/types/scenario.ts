export interface FlightPlanWaypointDto {
  fix: string;
  altitude?: number;
  constraint?: string;
  speed?: number;
  action?: string;
}

export interface FlightPlanDto {
  callsign: string;
  aircraftType?: string;
  departureAirport: string;
  arrivalAirport: string;
  cruiseAltitude: number;
  cruiseSpeed: number;
  route: FlightPlanWaypointDto[];
}

export interface InitialPositionDto {
  latitude: number;
  longitude: number;
  altitude: number;
  heading: number;
  groundSpeed: number;
  verticalSpeed: number;
}

export interface ScenarioAtisSeed {
  icao: string;
  runwayInUse: string;
}

export interface ScenarioAircraft {
  flightPlan: FlightPlanDto;
  initialPosition: InitialPositionDto;
  /** Relative seconds from simulation start. null/0 = immediate spawn. Reserved for future delayed spawn. */
  spawnTime?: number;
  /** Optional catalog procedure id; backend maps route like assign-procedure. */
  procedureId?: string;
  /** Optional SID enroute transition id (only with procedureId). */
  transitionId?: string;
}

export interface ScenarioJson {
  scenarioName?: string;
  description?: string;
  createdAt?: string;
  aircraft: ScenarioAircraft[];
  /** Optional ATIS runway seeds applied after reset-to-defaults on scenario load. */
  atis?: ScenarioAtisSeed[];
}
