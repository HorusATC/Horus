/**
 * Procedure ids from aip/rjtt-sid-star-20261101.json (slice 1) and
 * aip/slice3-20261101.json (slice 3, RJTT + RJAA). Keep in sync with the
 * catalog; quickstart sample procedureIds must satisfy the slice-1 union.
 */
export const RJTT_SLICE1_PROCEDURE_IDS = [
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

/** @deprecated Alias kept for quickstart imports; slice-1 RJTT catalog only. */
export const RJTT_CATALOG_PROCEDURE_IDS = RJTT_SLICE1_PROCEDURE_IDS;

export const SLICE3_CATALOG_PROCEDURE_IDS = [
  "LAXAS4.RWY16R",
  "LAXAS4.RWY16L",
  "LAXAS4.RWY34L_34R",
  "LAXAS4.RWY04",
  "LAXAS4.RWY05",
  "LAXAS4.RWY22",
  "NINOX4.RWY16R",
  "NINOX4.RWY16L",
  "NINOX4.RWY34L_34R",
  "NINOX4.RWY04",
  "NINOX4.RWY05",
  "NINOX4.RWY22",
  "TIARA2A.RWY16R",
  "TIARA2A.RWY16L",
  "TIARA2A.RWY34L_34R",
  "TIARA2A.RWY04",
  "TIARA2A.RWY05",
  "TIARA3B.RWY16R",
  "TIARA3B.RWY16L",
  "TIARA3B.RWY34R",
  "TIARA3B.RWY05",
  "TIARA3C.RWY16R",
  "TIARA3C.RWY16L",
  "TIARA3C.RWY34R",
  "TIARA3C.RWY05",
  "BEKLA3A.RWY16R",
  "BEKLA3A.RWY16L",
  "BEKLA3A.RWY34L_34R",
  "BEKLA3A.RWY04",
  "BEKLA3A.RWY05",
  "BEKLA4B.RWY16R",
  "BEKLA4B.RWY16L",
  "BEKLA4B.RWY34R",
  "BEKLA4B.RWY05",
  "BEKLA5C.RWY16R",
  "BEKLA5C.RWY16L",
  "BEKLA5C.RWY34R",
  "BEKLA5C.RWY05",
  "ROVER3A.RWY16R",
  "ROVER3A.RWY16L",
  "ROVER3A.RWY34L_34R",
  "ROVER3A.RWY04",
  "ROVER3A.RWY05",
  "ROVER4B.RWY16R",
  "ROVER4B.RWY16L",
  "ROVER4B.RWY34R",
  "ROVER4B.RWY05",
  "ROVER4C.RWY16R",
  "ROVER4C.RWY16L",
  "ROVER4C.RWY34R",
  "ROVER4C.RWY05",
  "ROVER4C.AKAGI",
  "ROVER4C.BRUCE",
  "ROVER4C.INUBO",
  "RUTAS4.RWY16R",
  "RUTAS4.RWY16L",
  "RUTAS4.RWY34L_34R",
  "RUTAS4.RWY04",
  "RUTAS4.RWY05",
  "RUTAS4.RWY22",
  "GUSRO1.RWY16R",
  "GUSRO1.RWY16L",
  "GUSRO1.RWY34L_34R",
  "GUSRO1.RWY04",
  "GUSRO1.RWY05",
  "GUSRO1.RWY22",
  "TETRA8.RWY16R",
  "TETRA8.RWY16L",
  "TETRA8.RWY34L",
  "TETRA8.RWY34R",
  "TETRA8.AGRIS",
  "TETRA8.KIMIN",
  "TETRA8.ENPAR",
  "REDEK2.RWY16R",
  "REDEK2.RWY16L",
  "REDEK2.RWY34L",
  "REDEK2.RWY34R",
  "PIGOK2.RWY16R",
  "PIGOK2.RWY16L",
  "PIGOK2.RWY34L",
  "PIGOK2.RWY34R",
  "GULBO2.RWY16R",
  "GULBO2.RWY16L",
  "GULBO2.RWY34L",
  "GULBO2.RWY34R",
  "BORLO2.RWY16R",
  "BORLO2.RWY16L",
  "BORLO2.RWY34L",
  "BORLO2.RWY34R",
  "PEDLA1.RWY16R",
  "PEDLA1.RWY16L",
  "PEDLA1.RWY34L",
  "PEDLA1.RWY34R",
  "OLVAN2.RWY16R",
  "OLVAN2.RWY16L",
  "OLVAN2.RWY34L",
  "OLVAN2.RWY34R",
  "OLVAN2.SAMUS",
  "RUTASE",
  "RUTAST",
  "SWAMPE",
  "SWAMPT",
  "SUPOKE",
  "SUPOKT",
  "LUBLAE",
  "LUBLAT",
  "RUTASG",
  "RUTASN",
  "SWAMPG",
  "SWAMPN",
  "SUPOKG",
  "SUPOKN",
  "LUBLAG",
  "LUBLAN",
] as const;

export const CATALOG_PROCEDURE_IDS = [
  ...RJTT_SLICE1_PROCEDURE_IDS,
  ...SLICE3_CATALOG_PROCEDURE_IDS,
] as const;

export const PROCEDURE_CATALOG_AIRPORTS = ["RJTT", "RJAA"] as const;

export type ProcedureCatalogAirport =
  (typeof PROCEDURE_CATALOG_AIRPORTS)[number];

export type RjttCatalogProcedureId =
  (typeof RJTT_CATALOG_PROCEDURE_IDS)[number];

export type Slice3CatalogProcedureId =
  (typeof SLICE3_CATALOG_PROCEDURE_IDS)[number];

export type CatalogProcedureId = (typeof CATALOG_PROCEDURE_IDS)[number];

export function isRjttCatalogProcedureId(
  id: string
): id is RjttCatalogProcedureId {
  return (RJTT_CATALOG_PROCEDURE_IDS as readonly string[]).includes(id);
}

export function isCatalogProcedureId(id: string): id is CatalogProcedureId {
  return (CATALOG_PROCEDURE_IDS as readonly string[]).includes(id);
}

export function isProcedureCatalogAirport(
  value: string
): value is ProcedureCatalogAirport {
  return (PROCEDURE_CATALOG_AIRPORTS as readonly string[]).includes(value);
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
