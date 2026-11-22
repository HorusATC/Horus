export type HoldingPatternPointDto = {
  latitude: number;
  longitude: number;
};

export type HoldEntryProcedure = "DIRECT" | "TEARDROP" | "PARALLEL";

/**
 * Instructed holding racetrack from Backend location DTO.
 * Vertices are authoritative; the radar must not recompute the geometry.
 */
export type HoldingPatternDto = {
  fixName: string;
  fixLatitude: number;
  fixLongitude: number;
  inboundCourseDeg: number;
  turnDirection: string;
  legMinutes?: number | null;
  legNm?: number | null;
  outboundLegNm: number;
  turnRateDegPerSec: number;
  turnRadiusNm: number;
  pattern: HoldingPatternPointDto[];
  /** AIM entry sector; predicted before fix, frozen after. */
  entryProcedure?: HoldEntryProcedure | null;
};

/** Closed ownership kinds for every HoldingPatternDto field. */
export type HoldingPatternDtoFieldKind = "required" | "optional";

/**
 * Exhaustive partition of every {@link HoldingPatternDto} key.
 * Adding a field without an entry (or an entry without a field) is a tsc error.
 */
export const HOLDING_PATTERN_DTO_FIELD_PARTITION = {
  fixName: "required",
  fixLatitude: "required",
  fixLongitude: "required",
  inboundCourseDeg: "required",
  turnDirection: "required",
  legMinutes: "optional",
  legNm: "optional",
  outboundLegNm: "required",
  turnRateDegPerSec: "required",
  turnRadiusNm: "required",
  pattern: "required",
  entryProcedure: "optional",
} as const satisfies Record<
  keyof HoldingPatternDto,
  HoldingPatternDtoFieldKind
>;

type HoldingPartitionKey = keyof typeof HOLDING_PATTERN_DTO_FIELD_PARTITION;
type _HoldingPartitionCovers =
  keyof HoldingPatternDto extends HoldingPartitionKey ? true : never;
type _HoldingPartitionNoExtras =
  HoldingPartitionKey extends keyof HoldingPatternDto ? true : never;
const _holdingPartitionCovers: _HoldingPartitionCovers = true;
const _holdingPartitionNoExtras: _HoldingPartitionNoExtras = true;
void _holdingPartitionCovers;
void _holdingPartitionNoExtras;

export function holdEntryProcedureLabel(
  entryProcedure: HoldEntryProcedure | null | undefined
): string | null {
  switch (entryProcedure) {
    case "DIRECT":
      return "DIR";
    case "TEARDROP":
      return "TDP";
    case "PARALLEL":
      return "PAR";
    default:
      return null;
  }
}
