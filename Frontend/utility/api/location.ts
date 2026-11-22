import { Coordinate } from "@/context/centerCoordinateContext";
import {
  Aircraft,
  type AircraftIdentityKey,
  type AircraftMotionVector,
  type PolledAircraftServerFields,
} from "../aircraft/aircraftClass";
import { CoordinateManager } from "../coordinateManager/CoordinateManager";
import { DisplayRange } from "@/context/displayRangeContext";
import {
  type HoldingPatternDto,
  type HoldingPatternPointDto,
} from "../aircraft/holdingPattern";
import { parseMsawAlert, type MsawAlertDto } from "../aircraft/msawAlert";
import { GLOBAL_SETTINGS } from "../globals/settings";
import {
  computeLabelPlacements,
  DEFAULT_LABEL_HIT_BOTTOM_OFFSET_PX,
  type LabelPlacementAircraft,
} from "../aircraft/labelPlacement";

export type { HoldingPatternDto, HoldingPatternPointDto };

export interface AircraftLocationDto {
  callsign: string;
  position: { latitude: number; longitude: number; altitude: number };
  vector: AircraftMotionVector;
  /** Pilot target from backend; distinct from ATC clearance memo (see spec 20260326-instruction-memo-radar-label). */
  instructedVector: {
    heading: number;
    groundSpeed: number;
    altitude: number;
    indicatedAirspeed?: number | null;
  };
  /** Controller clearance memo; omitted or null if not recorded. */
  atcClearance?: {
    heading: number;
    groundSpeed: number;
    altitude: number;
  } | null;
  type: string;
  model: string;
  originIata: string;
  originIcao: string;
  destinationIata: string;
  destinationIcao: string;
  /** ISO 8601 instant (e.g. …Z). Data block shows UTC HH:mm. */
  eta: string;
  squawk?: string | null;
  navigationMode: string;
  goAround?: boolean;
  towerHandoff?: boolean;
  riskLevel: number;
  /** Absent or null means no MSAW alert (tolerant of older backends). */
  msawAlert?: MsawAlertDto | null;
  /**
   * Instructed holding racetrack from Backend geometry.
   * Present only while navigationMode is HOLDING; omitted otherwise.
   * Polled (not client-only): Controller never issues Hold, so the overlay
   * must arrive on the location stream from the same vertices that drive flight.
   */
  holding?: HoldingPatternDto | null;
}

/**
 * DTO-side ownership relative to {@link Aircraft}.
 * - `identity`: set at Aircraft construction; not overwritten by polling
 * - `polledMapped`: feeds {@link PolledAircraftServerFields} (directly or derived)
 */
export type AircraftLocationDtoFieldKind = "identity" | "polledMapped";

/**
 * Exhaustive partition of every {@link AircraftLocationDto} key.
 * Adding a DTO field without an entry (or an entry without a DTO field) is a tsc error.
 */
export const AIRCRAFT_LOCATION_DTO_FIELD_PARTITION = {
  callsign: "identity",
  type: "identity",
  model: "identity",
  originIata: "identity",
  originIcao: "identity",
  destinationIata: "identity",
  destinationIcao: "identity",
  position: "polledMapped",
  vector: "polledMapped",
  instructedVector: "polledMapped",
  atcClearance: "polledMapped",
  eta: "polledMapped",
  squawk: "polledMapped",
  navigationMode: "polledMapped",
  goAround: "polledMapped",
  towerHandoff: "polledMapped",
  riskLevel: "polledMapped",
  msawAlert: "polledMapped",
  holding: "polledMapped",
} as const satisfies Record<
  keyof AircraftLocationDto,
  AircraftLocationDtoFieldKind
>;

type DtoPartitionKey = keyof typeof AIRCRAFT_LOCATION_DTO_FIELD_PARTITION;
type _DtoPartitionCoversInterface =
  keyof AircraftLocationDto extends DtoPartitionKey ? true : never;
type _DtoPartitionNoExtras = DtoPartitionKey extends keyof AircraftLocationDto
  ? true
  : never;
const _dtoPartitionCovers: _DtoPartitionCoversInterface = true;
const _dtoPartitionNoExtras: _DtoPartitionNoExtras = true;
void _dtoPartitionCovers;
void _dtoPartitionNoExtras;

type DtoIdentityKey = {
  [K in DtoPartitionKey]: (typeof AIRCRAFT_LOCATION_DTO_FIELD_PARTITION)[K] extends "identity"
    ? K
    : never;
}[DtoPartitionKey];

type DtoPolledMappedKey = {
  [K in DtoPartitionKey]: (typeof AIRCRAFT_LOCATION_DTO_FIELD_PARTITION)[K] extends "polledMapped"
    ? K
    : never;
}[DtoPartitionKey];

/**
 * Each Aircraft identity key is supplied by the same-named DTO identity key
 * at construction time only (not by polling).
 */
export const AIRCRAFT_IDENTITY_DTO_SOURCE = {
  callsign: "callsign",
  type: "type",
  model: "model",
  originIata: "originIata",
  originIcao: "originIcao",
  destinationIata: "destinationIata",
  destinationIcao: "destinationIcao",
} as const satisfies Record<AircraftIdentityKey, DtoIdentityKey>;

/**
 * Each polled Aircraft field is fed by a DTO polledMapped key.
 * `geoPosition` comes from DTO `position`; `indicatedAirspeedKnots` from
 * `instructedVector.indicatedAirspeed`.
 */
export const POLLED_AIRCRAFT_DTO_SOURCE = {
  position: "position",
  geoPosition: "position",
  vector: "vector",
  instructedVector: "instructedVector",
  indicatedAirspeedKnots: "instructedVector",
  atcClearance: "atcClearance",
  squawk: "squawk",
  eta: "eta",
  navigationMode: "navigationMode",
  riskLevel: "riskLevel",
  goAround: "goAround",
  towerHandoff: "towerHandoff",
  msawAlert: "msawAlert",
  holding: "holding",
} as const satisfies Record<
  keyof PolledAircraftServerFields,
  DtoPolledMappedKey
>;

type MappedDtoPolledKeys =
  (typeof POLLED_AIRCRAFT_DTO_SOURCE)[keyof typeof POLLED_AIRCRAFT_DTO_SOURCE];
type _EveryDtoPolledKeyIsMapped = DtoPolledMappedKey extends MappedDtoPolledKeys
  ? true
  : never;
type _NoExtraMappedDtoKeys = MappedDtoPolledKeys extends DtoPolledMappedKey
  ? true
  : never;
const _dtoPolledMappingCovers: _EveryDtoPolledKeyIsMapped = true;
const _dtoPolledMappingNoExtras: _NoExtraMappedDtoKeys = true;
void _dtoPolledMappingCovers;
void _dtoPolledMappingNoExtras;

type _IdentityKeysAlign = AircraftIdentityKey extends DtoIdentityKey
  ? DtoIdentityKey extends AircraftIdentityKey
    ? true
    : never
  : never;
const _identityKeysAlign: _IdentityKeysAlign = true;
void _identityKeysAlign;

export const fetchAircraftLocation = async (
  controllingAircrafts: Aircraft[],
  centerCoordinate: Coordinate,
  displayRange: DisplayRange
) => {
  let updatedControllingAircraft: Aircraft[] = [];
  try {
    const response = await fetch("/api/aircraft/location/all", {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    });

    if (response.ok) {
      const data = (await response.json()) as AircraftLocationDto[];
      const aircraftData = data.map((dto) =>
        mapDtoToAircraft(dto, centerCoordinate, displayRange)
      );
      updatedControllingAircraft = updateControllingAircrafts(
        aircraftData,
        controllingAircrafts
      );
    } else {
      console.error("Request failed with status:", response.status);
    }
  } catch (error) {
    console.error("Error occurred while fetching aircraft location:", error);
  }
  return updatedControllingAircraft;
};

function mapDtoToAircraft(
  dto: AircraftLocationDto,
  centerCoordinate: Coordinate,
  displayRange: DisplayRange
): Aircraft {
  const coordinateOnCanvas = CoordinateManager.calculateCanvasCoordinates(
    { latitude: dto.position.latitude, longitude: dto.position.longitude },
    centerCoordinate,
    displayRange
  );

  const atc =
    dto.atcClearance != null
      ? {
          heading: dto.atcClearance.heading,
          groundSpeed: dto.atcClearance.groundSpeed,
          altitude: dto.atcClearance.altitude,
        }
      : null;

  return new Aircraft(
    dto.callsign,
    {
      x: coordinateOnCanvas.x,
      y: coordinateOnCanvas.y,
      altitude: dto.position.altitude,
    },
    {
      latitude: dto.position.latitude,
      longitude: dto.position.longitude,
    },
    {
      heading: dto.vector.heading,
      groundSpeed: dto.vector.groundSpeed,
      verticalSpeed: dto.vector.verticalSpeed,
      track: dto.vector.track ?? dto.vector.heading,
    },
    {
      heading: dto.instructedVector.heading,
      groundSpeed: dto.instructedVector.groundSpeed,
      altitude: dto.instructedVector.altitude,
    },
    atc,
    dto.type,
    dto.model,
    dto.originIata ?? "",
    dto.originIcao ?? "",
    dto.destinationIata ?? "",
    dto.destinationIcao ?? "",
    dto.squawk ?? null,
    dto.eta ?? "",
    50,
    50,
    dto.riskLevel ?? 0,
    dto.navigationMode ?? "HEADING",
    dto.instructedVector.indicatedAirspeed ?? null,
    dto.goAround === true,
    dto.towerHandoff === true,
    parseMsawAlert(dto.msawAlert),
    parseHoldingPattern(dto.holding)
  );
}

function parseHoldingPattern(
  holding: HoldingPatternDto | null | undefined
): HoldingPatternDto | null {
  if (holding == null) {
    return null;
  }
  if (!Array.isArray(holding.pattern) || holding.pattern.length < 2) {
    return null;
  }
  return holding;
}

function toLabelPlacementAircraft(aircraft: Aircraft): LabelPlacementAircraft {
  return {
    callsign: aircraft.callsign,
    position: aircraft.position,
    label: aircraft.label,
    labelPinned: aircraft.labelPinned,
    hitBottomOffsetPx: DEFAULT_LABEL_HIT_BOTTOM_OFFSET_PX,
  };
}

function updateControllingAircrafts(
  apiResponse: Aircraft[],
  controllingAircrafts: Aircraft[]
): Aircraft[] {
  const newAircraftMap = new Map<string, Aircraft>();
  const newlyAppearedCallsigns = new Set<string>();

  apiResponse.forEach((aircraft) => {
    newAircraftMap.set(aircraft.callsign, aircraft);
  });

  controllingAircrafts = controllingAircrafts.filter((airplane) => {
    const newAircraft = newAircraftMap.get(airplane.callsign);
    if (newAircraft) {
      airplane.applyPolledFields(newAircraft);
      newAircraftMap.delete(airplane.callsign);
      return true;
    } else {
      return false;
    }
  });

  newAircraftMap.forEach((newAircraft) => {
    newlyAppearedCallsigns.add(newAircraft.callsign);
    const newAirplane = new Aircraft(
      newAircraft.callsign,
      newAircraft.position,
      newAircraft.geoPosition,
      newAircraft.vector,
      newAircraft.instructedVector,
      newAircraft.atcClearance,
      newAircraft.type,
      newAircraft.model,
      newAircraft.originIata,
      newAircraft.originIcao,
      newAircraft.destinationIata,
      newAircraft.destinationIcao,
      newAircraft.squawk,
      newAircraft.eta,
      50,
      50,
      newAircraft.riskLevel,
      newAircraft.navigationMode,
      newAircraft.indicatedAirspeedKnots,
      newAircraft.goAround,
      newAircraft.towerHandoff,
      newAircraft.msawAlert,
      newAircraft.holding,
      false
    );
    controllingAircrafts.push(newAirplane);
  });

  computeLabelPlacements(controllingAircrafts.map(toLabelPlacementAircraft), {
    canvas: {
      width: GLOBAL_SETTINGS.canvasWidth,
      height: GLOBAL_SETTINGS.canvasHeight,
    },
    forcePlaceCallsigns: newlyAppearedCallsigns,
  });

  return controllingAircrafts;
}
