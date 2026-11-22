import quickstartSampleJson from "@/data/quickstart-arrival-sample.json";
import {
  QUICKSTART_STAR_PROCEDURE_IDS,
  isRjttCatalogProcedureId,
  type RjttCatalogProcedureId,
} from "@/types/rjttProcedureIds";
import type { ScenarioJson } from "@/types/scenario";

/** Typed view of the default START WITH SAMPLE scenario. */
export const quickstartArrivalSample = quickstartSampleJson as ScenarioJson;

function readSampleProcedureIds(): RjttCatalogProcedureId[] {
  return quickstartArrivalSample.aircraft.map((ac) => {
    const id = ac.procedureId;
    if (id == null || !isRjttCatalogProcedureId(id)) {
      throw new Error(
        `quickstart-arrival-sample.json procedureId is missing or not in catalog: ${String(id)}`
      );
    }
    return id;
  });
}

export const QUICKSTART_SAMPLE_PROCEDURE_IDS = readSampleProcedureIds();

/** Compile-time: golden STAR id list is a subset of the catalog union. */
const _goldenSatisfiesCatalog: readonly RjttCatalogProcedureId[] =
  QUICKSTART_STAR_PROCEDURE_IDS;
void _goldenSatisfiesCatalog;

export function assertQuickstartSampleProcedureIdsAgainstCatalog(): void {
  if (
    QUICKSTART_SAMPLE_PROCEDURE_IDS.length !==
    QUICKSTART_STAR_PROCEDURE_IDS.length
  ) {
    throw new Error(
      `Expected ${QUICKSTART_STAR_PROCEDURE_IDS.length} STAR procedureIds, got ${QUICKSTART_SAMPLE_PROCEDURE_IDS.length}`
    );
  }
  for (let i = 0; i < QUICKSTART_STAR_PROCEDURE_IDS.length; i++) {
    if (
      QUICKSTART_SAMPLE_PROCEDURE_IDS[i] !== QUICKSTART_STAR_PROCEDURE_IDS[i]
    ) {
      throw new Error(
        `procedureId order mismatch at ${i}: expected ${QUICKSTART_STAR_PROCEDURE_IDS[i]}, got ${QUICKSTART_SAMPLE_PROCEDURE_IDS[i]}`
      );
    }
  }
  const atis = quickstartArrivalSample.atis;
  if (
    atis == null ||
    atis.length !== 1 ||
    atis[0]?.icao !== "RJTT" ||
    atis[0]?.runwayInUse !== "34L"
  ) {
    throw new Error("quickstart sample must seed RJTT runwayInUse 34L");
  }
}
