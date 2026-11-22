import {
  assertQuickstartDepartureSampleAgainstCatalog,
  QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS,
  QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS,
  quickstartDepartureSample,
} from "@/data/quickstartDepartureSample";
import {
  QUICKSTART_DEPARTURE_PROCEDURE_IDS,
  QUICKSTART_DEPARTURE_TRANSITION_IDS,
  RJTT_CATALOG_PROCEDURE_IDS,
  isRjttCatalogProcedureId,
} from "@/types/rjttProcedureIds";

describe("quickstartDepartureSample catalog binding", () => {
  it("binds VAMOS procedure/transition ids and ATIS 34L without VISIP", () => {
    expect(() => assertQuickstartDepartureSampleAgainstCatalog()).not.toThrow();
    expect(QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS).toEqual([
      ...QUICKSTART_DEPARTURE_PROCEDURE_IDS,
    ]);
    expect(QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS).toEqual([
      ...QUICKSTART_DEPARTURE_TRANSITION_IDS,
    ]);
    expect(quickstartDepartureSample.aircraft).toHaveLength(1);

    for (const id of QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
      expect(RJTT_CATALOG_PROCEDURE_IDS).toContain(id);
      expect(id).not.toBe("VISIP1");
    }
    for (const id of QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
    }
  });
});
