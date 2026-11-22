import {
  CATALOG_PROCEDURE_IDS,
  PROCEDURE_CATALOG_AIRPORTS,
  QUICKSTART_DEPARTURE_PROCEDURE_IDS,
  QUICKSTART_DEPARTURE_TRANSITION_IDS,
  QUICKSTART_STAR_PROCEDURE_IDS,
  RJTT_CATALOG_PROCEDURE_IDS,
  SLICE3_CATALOG_PROCEDURE_IDS,
  isCatalogProcedureId,
  isProcedureCatalogAirport,
  isRjttCatalogProcedureId,
} from "@/types/rjttProcedureIds";

describe("procedure catalog ids", () => {
  it("keeps slice-1 quickstart ids inside the RJTT slice-1 union", () => {
    for (const id of QUICKSTART_STAR_PROCEDURE_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
      expect(RJTT_CATALOG_PROCEDURE_IDS).toContain(id);
    }
    for (const id of QUICKSTART_DEPARTURE_PROCEDURE_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
    }
    for (const id of QUICKSTART_DEPARTURE_TRANSITION_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
    }
  });

  it("includes slice-3 RJTT and RJAA procedure ids without colliding with slice 1", () => {
    expect(SLICE3_CATALOG_PROCEDURE_IDS).toHaveLength(114);
    expect(SLICE3_CATALOG_PROCEDURE_IDS).toContain("LAXAS4.RWY16R");
    expect(SLICE3_CATALOG_PROCEDURE_IDS).toContain("TETRA8.RWY16R");
    expect(SLICE3_CATALOG_PROCEDURE_IDS).toContain("RUTASE");
    for (const id of SLICE3_CATALOG_PROCEDURE_IDS) {
      expect(RJTT_CATALOG_PROCEDURE_IDS).not.toContain(id);
      expect(isCatalogProcedureId(id)).toBe(true);
    }
    expect(CATALOG_PROCEDURE_IDS).toHaveLength(
      RJTT_CATALOG_PROCEDURE_IDS.length + SLICE3_CATALOG_PROCEDURE_IDS.length
    );
  });

  it("accepts only RJTT and RJAA as procedure catalog airports", () => {
    expect(PROCEDURE_CATALOG_AIRPORTS).toEqual(["RJTT", "RJAA"]);
    expect(isProcedureCatalogAirport("RJTT")).toBe(true);
    expect(isProcedureCatalogAirport("RJAA")).toBe(true);
    expect(isProcedureCatalogAirport("RJBB")).toBe(false);
  });
});
