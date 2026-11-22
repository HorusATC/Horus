export type HoldingPatternPointDto = {
  latitude: number;
  longitude: number;
};

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
};
