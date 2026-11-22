/** Single source of truth for range/bearing, threshold distance, CPA, and readout formats. */

export const EARTH_RADIUS_KM = 6378.1;
export const NM_TO_KM = 1.852;
/** Backend GlobalConstants.EARTH_RADIUS_NM = 6378.1 / 1.852 */
export const EARTH_RADIUS_NM = EARTH_RADIUS_KM / NM_TO_KM;

export const APPROACH_CORRIDOR_EXTENSION_NM = 10.0;
export const APPROACH_CORRIDOR_HALF_WIDTH_NM = 1.0;
export const MIN_GROUND_SPEED_KT_FOR_TIME = 1;
export const MAX_TIME_SECONDS_FOR_DISPLAY = 60 * 60;

export type LatLon = {
  latitude: number;
  longitude: number;
};

export type HorizontalVelocity = {
  headingDeg: number;
  groundSpeedKt: number;
};

export type ApproachCorridorProjection = {
  alongTrackNm: number;
  crossTrackNm: number;
  inCorridor: boolean;
};

export type ThresholdDistanceResult = {
  airportIcao: string;
  designator: string;
  approachCourseHeading: number;
  alongTrackNm: number;
  crossTrackNm: number;
};

export type RunwayApproachGeometry = {
  airportIcao: string;
  designator: string;
  thresholdLatitude: number;
  thresholdLongitude: number;
  /** Inbound approach course (degrees true / simulator heading). */
  approachCourseHeading: number;
};

function toRadians(deg: number): number {
  return (deg * Math.PI) / 180;
}

function toDegrees(rad: number): number {
  return (rad * 180) / Math.PI;
}

export function normalizeBearingDeg(deg: number): number {
  const m = deg % 360;
  return m < 0 ? m + 360 : m;
}

/** Spherical Haversine distance in nautical miles (always spherical; no planar shortcut). */
export function haversineDistanceNm(a: LatLon, b: LatLon): number {
  const lat1 = toRadians(a.latitude);
  const lat2 = toRadians(b.latitude);
  const dLat = toRadians(b.latitude - a.latitude);
  const dLon = toRadians(b.longitude - a.longitude);
  const sinDLat = Math.sin(dLat * 0.5);
  const sinDLon = Math.sin(dLon * 0.5);
  const h =
    sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
  const c = 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
  return EARTH_RADIUS_NM * c;
}

/** Forward azimuth from `from` to `to` (degrees, 0–360). */
export function initialBearingDeg(from: LatLon, to: LatLon): number {
  const lat1 = toRadians(from.latitude);
  const lat2 = toRadians(to.latitude);
  const dLon = toRadians(to.longitude - from.longitude);
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x =
    Math.cos(lat1) * Math.sin(lat2) -
    Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  return normalizeBearingDeg(toDegrees(Math.atan2(y, x)));
}

/** Destination point after traveling `distanceNm` on initial bearing `bearingDeg`. */
export function destinationPoint(
  from: LatLon,
  bearingDeg: number,
  distanceNm: number
): LatLon {
  const lat1 = toRadians(from.latitude);
  const lon1 = toRadians(from.longitude);
  const brng = toRadians(bearingDeg);
  const angDist = distanceNm / EARTH_RADIUS_NM;
  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(angDist) +
      Math.cos(lat1) * Math.sin(angDist) * Math.cos(brng)
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(brng) * Math.sin(angDist) * Math.cos(lat1),
      Math.cos(angDist) - Math.sin(lat1) * Math.sin(lat2)
    );
  return { latitude: toDegrees(lat2), longitude: toDegrees(lon2) };
}

/**
 * Along-track / cross-track from threshold along the approach extension
 * (outbound = approachCourseHeading + 180), matching MSAW corridor geometry.
 */
export function projectOntoApproachCorridor(
  point: LatLon,
  threshold: LatLon,
  approachCourseHeading: number
): ApproachCorridorProjection {
  const distanceNm = haversineDistanceNm(threshold, point);
  const bearing = initialBearingDeg(threshold, point);
  const outbound = normalizeBearingDeg(approachCourseHeading + 180);
  let bearingDiff = Math.abs(normalizeBearingDeg(bearing - outbound));
  if (bearingDiff > 180) {
    bearingDiff = 360 - bearingDiff;
  }
  if (bearingDiff > 90) {
    return { alongTrackNm: -distanceNm, crossTrackNm: 0, inCorridor: false };
  }
  const alongTrackNm = distanceNm * Math.cos(toRadians(bearingDiff));
  const crossTrackNm = distanceNm * Math.sin(toRadians(bearingDiff));
  const inCorridor =
    alongTrackNm > 0 &&
    alongTrackNm <= APPROACH_CORRIDOR_EXTENSION_NM + 1e-12 &&
    Math.abs(crossTrackNm) <= APPROACH_CORRIDOR_HALF_WIDTH_NM + 1e-12;
  return { alongTrackNm, crossTrackNm, inCorridor };
}

export function isInApproachCorridor(
  point: LatLon,
  threshold: LatLon,
  approachCourseHeading: number
): boolean {
  return projectOntoApproachCorridor(point, threshold, approachCourseHeading)
    .inCorridor;
}

/** Prefer the shortest along-track among catalog runways whose corridor contains the point. */
export function findThresholdDistance(
  point: LatLon,
  runways: readonly RunwayApproachGeometry[]
): ThresholdDistanceResult | null {
  let best: ThresholdDistanceResult | null = null;
  for (const rwy of runways) {
    const projection = projectOntoApproachCorridor(
      point,
      {
        latitude: rwy.thresholdLatitude,
        longitude: rwy.thresholdLongitude,
      },
      rwy.approachCourseHeading
    );
    if (!projection.inCorridor) {
      continue;
    }
    if (best == null || projection.alongTrackNm < best.alongTrackNm) {
      best = {
        airportIcao: rwy.airportIcao,
        designator: rwy.designator,
        approachCourseHeading: rwy.approachCourseHeading,
        alongTrackNm: projection.alongTrackNm,
        crossTrackNm: projection.crossTrackNm,
      };
    }
  }
  return best;
}

/** Time to cover `distanceNm` at constant ground speed; null if GS too low or ≥ 60 min. */
export function timeToDistanceSeconds(
  distanceNm: number,
  groundSpeedKt: number
): number | null {
  if (!(groundSpeedKt >= MIN_GROUND_SPEED_KT_FOR_TIME) || distanceNm < 0) {
    return null;
  }
  const seconds = (distanceNm / groundSpeedKt) * 3600;
  if (seconds >= MAX_TIME_SECONDS_FOR_DISPLAY) {
    return null;
  }
  return seconds;
}

function velocityEastNorthKt(v: HorizontalVelocity): {
  eastKt: number;
  northKt: number;
} {
  const rad = toRadians(v.headingDeg);
  return {
    eastKt: v.groundSpeedKt * Math.sin(rad),
    northKt: v.groundSpeedKt * Math.cos(rad),
  };
}

function relativeEastNorthNm(
  from: LatLon,
  to: LatLon
): {
  eastNm: number;
  northNm: number;
} {
  const dist = haversineDistanceNm(from, to);
  const brg = toRadians(initialBearingDeg(from, to));
  return {
    eastNm: dist * Math.sin(brg),
    northNm: dist * Math.cos(brg),
  };
}

/**
 * Horizontal CPA time (seconds) under constant heading/GS, ignoring altitude.
 * Null when diverging (t ≤ 0), relative speed ~0, or time ≥ 60 min.
 */
export function horizontalCpaSeconds(
  aPos: LatLon,
  aVel: HorizontalVelocity,
  bPos: LatLon,
  bVel: HorizontalVelocity
): number | null {
  const r = relativeEastNorthNm(aPos, bPos);
  const va = velocityEastNorthKt(aVel);
  const vb = velocityEastNorthKt(bVel);
  const relEast = vb.eastKt - va.eastKt;
  const relNorth = vb.northKt - va.northKt;
  const speed2 = relEast * relEast + relNorth * relNorth;
  if (speed2 < 1e-12) {
    return null;
  }
  const tHours = -(r.eastNm * relEast + r.northNm * relNorth) / speed2;
  const tSeconds = tHours * 3600;
  if (tSeconds <= 0 || tSeconds >= MAX_TIME_SECONDS_FOR_DISPLAY) {
    return null;
  }
  return tSeconds;
}

export function formatDistanceNm(distanceNm: number): string {
  return `${distanceNm.toFixed(1)}NM`;
}

export function formatTrueBearingDeg(bearingDeg: number): string {
  const rounded = Math.round(normalizeBearingDeg(bearingDeg)) % 360;
  return `${rounded.toString().padStart(3, "0")}T`;
}

export function formatDurationMss(seconds: number): string {
  const total = Math.round(seconds);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

export function formatThresholdReadout(
  designator: string,
  distanceNm: number,
  timeSeconds: number | null
): string {
  const base = `${designator} ${formatDistanceNm(distanceNm)}`;
  if (timeSeconds == null) {
    return base;
  }
  return `${base} ${formatDurationMss(timeSeconds)}`;
}

export function formatRblLabel(
  distanceNm: number,
  bearingDeg: number,
  cpaSeconds: number | null
): string {
  const base = `${formatDistanceNm(distanceNm)} ${formatTrueBearingDeg(bearingDeg)}`;
  if (cpaSeconds == null) {
    return base;
  }
  return `${base} ${formatDurationMss(cpaSeconds)}`;
}
