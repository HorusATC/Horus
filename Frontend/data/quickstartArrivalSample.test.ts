import {
  assertQuickstartSampleProcedureIdsAgainstCatalog,
  QUICKSTART_SAMPLE_PROCEDURE_IDS,
  quickstartArrivalSample,
} from "@/data/quickstartArrivalSample";
import {
  QUICKSTART_STAR_PROCEDURE_IDS,
  RJTT_CATALOG_PROCEDURE_IDS,
  isRjttCatalogProcedureId,
} from "@/types/rjttProcedureIds";

describe("quickstartArrivalSample catalog binding", () => {
  it("binds procedureIds to typed catalog ids and ATIS 34L", () => {
    expect(() =>
      assertQuickstartSampleProcedureIdsAgainstCatalog()
    ).not.toThrow();
    expect(QUICKSTART_SAMPLE_PROCEDURE_IDS).toEqual([
      ...QUICKSTART_STAR_PROCEDURE_IDS,
    ]);
    expect(quickstartArrivalSample.aircraft).toHaveLength(5);

    for (const id of QUICKSTART_SAMPLE_PROCEDURE_IDS) {
      expect(isRjttCatalogProcedureId(id)).toBe(true);
      expect(RJTT_CATALOG_PROCEDURE_IDS).toContain(id);
    }
  });
});
