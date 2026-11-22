/**
 * Procedure ids from aip/rjtt-sid-star-20261101.json (AIRAC 1 Nov 2026 extract).
 * Keep in sync with the catalog; quickstart sample procedureIds must satisfy this union.
 */
export const RJTT_CATALOG_PROCEDURE_IDS = [
  "OSHIMA1A",
  "OSHIMA1K",
  "OSHIMA2C",
  "AKSEL1A",
  "AKSEL1K",
  "AKSEL2C",
  "AROSA1A",
  "AROSA1K",
  "AROSA2C",
  "POLIX2A",
  "POLIX2K",
  "POLIX1C",
  "GODIN2A",
  "GODIN2K",
  "GODIN1C",
  "VAMOS4.RWY16R",
  "VAMOS4.RWY16L",
  "VAMOS4.RWY34L_34R",
  "VAMOS4.RWY04",
  "VAMOS4.RWY05",
  "VAMOS4.RWY22",
  "VAMOS4.TATEYAMA",
  "VAMOS4.DRAKY",
  "VISIP1",
] as const;

export type RjttCatalogProcedureId =
  (typeof RJTT_CATALOG_PROCEDURE_IDS)[number];

export function isRjttCatalogProcedureId(
  id: string
): id is RjttCatalogProcedureId {
  return (RJTT_CATALOG_PROCEDURE_IDS as readonly string[]).includes(id);
}

/** Default START WITH SAMPLE STAR assignments (must exist in the catalog). */
export const QUICKSTART_STAR_PROCEDURE_IDS = [
  "OSHIMA1A",
  "AKSEL1K",
  "AROSA2C",
  "AKSEL2C",
  "GODIN2A",
] as const satisfies readonly RjttCatalogProcedureId[];

/**
 * Loadable VAMOS FOUR departure sample (separate from the arrival default).
 * Transition ids are optional on the scenario aircraft and checked separately.
 */
export const QUICKSTART_DEPARTURE_PROCEDURE_IDS = [
  "VAMOS4.RWY34L_34R",
] as const satisfies readonly RjttCatalogProcedureId[];

export const QUICKSTART_DEPARTURE_TRANSITION_IDS = [
  "VAMOS4.TATEYAMA",
] as const satisfies readonly RjttCatalogProcedureId[];
