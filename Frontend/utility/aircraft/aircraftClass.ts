import type { HoldingPatternDto } from "./holdingPattern";
import type { MsawAlertDto } from "./msawAlert";

export type AtcClearanceVector = {
  heading: number;
  groundSpeed: number;
  altitude: number;
};

/** Motion vector from location poll; `track` may be absent on older backends. */
export type AircraftMotionVector = {
  heading: number;
  groundSpeed: number;
  verticalSpeed: number;
  track?: number;
};

/** Past geographic samples for track dots; canvas coords are derived at draw time. */
export type PositionHistoryPoint = {
  latitude: number;
  longitude: number;
  altitude: number;
  /** ISO 8601 instant when this sample was recorded on the client. */
  recordedAt: string;
};

/** Enough for 90s track marks at ~1 Hz polls plus margin. */
export const POSITION_HISTORY_MAX_POINTS = 120;

/**
 * Data-property keys of {@link Aircraft} (methods excluded).
 * Used so a new class field must land in {@link AIRCRAFT_FIELD_PARTITION}.
 */
export type AircraftDataKey = {
  [K in keyof Aircraft]: Aircraft[K] extends (...args: never[]) => unknown
    ? never
    : K;
}[keyof Aircraft];

/** Closed ownership kinds for every Aircraft data field. */
export type AircraftFieldKind = "client" | "identity" | "polled";

/**
 * Exhaustive partition of Aircraft data keys.
 * Adding a field to the class without an entry here is a TypeScript error;
 * an entry without a class field is also an error (`satisfies` + reverse check).
 */
export const AIRCRAFT_FIELD_PARTITION = {
  label: "client",
  labelPinned: "client",
  positionHistory: "client",
  callsign: "identity",
  type: "identity",
  model: "identity",
  originIata: "identity",
  originIcao: "identity",
  destinationIata: "identity",
  destinationIcao: "identity",
  position: "polled",
  geoPosition: "polled",
  vector: "polled",
  instructedVector: "polled",
  atcClearance: "polled",
  squawk: "polled",
  eta: "polled",
  navigationMode: "polled",
  riskLevel: "polled",
  indicatedAirspeedKnots: "polled",
  goAround: "polled",
  towerHandoff: "polled",
  msawAlert: "polled",
  holding: "polled",
} as const satisfies Record<AircraftDataKey, AircraftFieldKind>;

type AircraftPartitionKey = keyof typeof AIRCRAFT_FIELD_PARTITION;
type _AircraftPartitionCoversClass =
  AircraftDataKey extends AircraftPartitionKey ? true : never;
type _AircraftPartitionNoExtras = AircraftPartitionKey extends AircraftDataKey
  ? true
  : never;
const _aircraftPartitionCovers: _AircraftPartitionCoversClass = true;
const _aircraftPartitionNoExtras: _AircraftPartitionNoExtras = true;
void _aircraftPartitionCovers;
void _aircraftPartitionNoExtras;

type PolledAircraftKey = {
  [K in AircraftPartitionKey]: (typeof AIRCRAFT_FIELD_PARTITION)[K] extends "polled"
    ? K
    : never;
}[AircraftPartitionKey];

export type AircraftIdentityKey = {
  [K in AircraftPartitionKey]: (typeof AIRCRAFT_FIELD_PARTITION)[K] extends "identity"
    ? K
    : never;
}[AircraftPartitionKey];

/**
 * Server-owned Aircraft fields refreshed from each location poll.
 * Client-owned (`label`, `labelPinned`, `positionHistory`) and identity fields
 * are excluded; identity is set at construction and not overwritten by polling.
 */
export type PolledAircraftServerFields = Pick<Aircraft, PolledAircraftKey>;

/**
 * Exhaustive key checklist for {@link PolledAircraftServerFields}.
 * Adding a polled partition entry without an entry here is a TypeScript error.
 */
export const POLLED_AIRCRAFT_SERVER_FIELD_KEYS = {
  position: true,
  geoPosition: true,
  vector: true,
  instructedVector: true,
  atcClearance: true,
  squawk: true,
  eta: true,
  navigationMode: true,
  riskLevel: true,
  indicatedAirspeedKnots: true,
  goAround: true,
  towerHandoff: true,
  msawAlert: true,
  holding: true,
} as const satisfies Record<keyof PolledAircraftServerFields, true>;

export class Aircraft {
  callsign: string;
  position: { x: number; y: number; altitude: number };
  /** WGS-84 from last API sample; kept in sync with `position` for history / replay. */
  geoPosition: { latitude: number; longitude: number };
  vector: AircraftMotionVector;
  /**
   * Pilot-applied target from Operator (POST /control). Not the controller’s clearance memo;
   * see spec/20260326-instruction-memo-radar-label/spec.md.
   */
  instructedVector: { heading: number; groundSpeed: number; altitude: number };
  /** Active IAS target from backend when heading-mode speed instruction uses indicated airspeed. */
  indicatedAirspeedKnots: number | null;
  /** Controller-recorded clearance memo from POST .../atc-clearance; null if none. */
  atcClearance: AtcClearanceVector | null;
  type: string; // 航空機カテゴリ（Commercial, Military, Helicopter）
  model: string; // 航空機機種（B738, F-35A, UH-60J等）
  originIata: string;
  originIcao: string;
  destinationIata: string;
  destinationIcao: string;
  squawk: string | null;
  navigationMode: string;
  /** True while a go-around climb on runway heading is the active instruction. */
  goAround: boolean;
  /** True after the approach seat has transferred the aircraft to the tower. */
  towerHandoff: boolean;
  /** ISO 8601 instant from API; data block shows UTC HH:mm (see spec 20260318-data-block-display-items). */
  eta: string;
  label: { x: number; y: number };
  /**
   * When true, auto-placement must not move `label` (set after the user drags).
   * Client-owned; never written by location polling.
   */
  labelPinned: boolean;
  riskLevel: number; // 危険度（0-100）
  /** MSAW alert from Backend; null when no alert (or older Backend omitting the field). */
  msawAlert: MsawAlertDto | null;
  /**
   * Instructed holding pattern from location poll; null when not holding.
   * Cleared automatically when Backend omits `holding` after resume/replace.
   */
  holding: HoldingPatternDto | null;
  positionHistory: PositionHistoryPoint[];

  constructor(
    callsign: string,
    position: { x: number; y: number; altitude: number },
    geoPosition: { latitude: number; longitude: number },
    vector: AircraftMotionVector,
    instructedVector: {
      heading: number;
      groundSpeed: number;
      altitude: number;
    },
    atcClearance: AtcClearanceVector | null = null,
    type: string,
    model: string,
    originIata: string,
    originIcao: string,
    destinationIata: string,
    destinationIcao: string,
    squawk: string | null,
    eta: string,
    labelX: number = 50,
    labelY: number = 50,
    riskLevel: number = 0,
    navigationMode: string = "HEADING",
    indicatedAirspeedKnots: number | null = null,
    goAround: boolean = false,
    towerHandoff: boolean = false,
    msawAlert: MsawAlertDto | null = null,
    holding: HoldingPatternDto | null = null,
    labelPinned: boolean = false
  ) {
    this.callsign = callsign;
    this.position = position;
    this.geoPosition = geoPosition;
    this.vector = vector;
    this.instructedVector = instructedVector;
    this.atcClearance = atcClearance;
    this.type = type;
    this.model = model;
    this.originIata = originIata;
    this.originIcao = originIcao;
    this.destinationIata = destinationIata;
    this.destinationIcao = destinationIcao;
    this.squawk = squawk;
    this.eta = eta;
    this.label = { x: labelX, y: labelY };
    this.labelPinned = labelPinned;
    this.riskLevel = riskLevel;
    this.navigationMode = navigationMode;
    this.indicatedAirspeedKnots = indicatedAirspeedKnots;
    this.goAround = goAround;
    this.towerHandoff = towerHandoff;
    this.msawAlert = msawAlert;
    this.holding = holding;
    this.positionHistory = [];
  }

  private recordPastGeoSample(): void {
    this.positionHistory.push({
      latitude: this.geoPosition.latitude,
      longitude: this.geoPosition.longitude,
      altitude: this.position.altitude,
      recordedAt: new Date().toISOString(),
    });
    while (this.positionHistory.length > POSITION_HISTORY_MAX_POINTS) {
      this.positionHistory.shift();
    }
  }

  /**
   * Apply server-owned fields from a location poll while keeping client-owned
   * `label` / `labelPinned` / `positionHistory` and identity fields. Keys are
   * driven by {@link POLLED_AIRCRAFT_SERVER_FIELD_KEYS} so a new polled field
   * cannot be silently skipped (tsc error via `satisfies`).
   */
  public applyPolledFields(source: PolledAircraftServerFields): void {
    this.recordPastGeoSample();
    // Explicit object must list every PolledAircraftServerFields key; combined
    // with POLLED_AIRCRAFT_SERVER_FIELD_KEYS `satisfies`, a new server-owned
    // field cannot be silently skipped.
    const next = {
      position: source.position,
      geoPosition: source.geoPosition,
      vector: source.vector,
      instructedVector: source.instructedVector,
      atcClearance: source.atcClearance,
      squawk: source.squawk,
      eta: source.eta,
      navigationMode: source.navigationMode,
      riskLevel: source.riskLevel,
      indicatedAirspeedKnots: source.indicatedAirspeedKnots,
      goAround: source.goAround,
      towerHandoff: source.towerHandoff,
      msawAlert: source.msawAlert,
      holding: source.holding,
    } satisfies PolledAircraftServerFields;
    const _keysMatchChecklist: Record<keyof typeof next, true> =
      POLLED_AIRCRAFT_SERVER_FIELD_KEYS;
    void _keysMatchChecklist;
    Object.assign(this, next);
  }
}
