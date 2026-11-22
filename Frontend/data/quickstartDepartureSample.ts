import quickstartDepartureSampleJson from "@/data/quickstart-departure-sample.json";
import {
  QUICKSTART_DEPARTURE_PROCEDURE_IDS,
  QUICKSTART_DEPARTURE_TRANSITION_IDS,
  isRjttCatalogProcedureId,
  type RjttCatalogProcedureId,
} from "@/types/rjttProcedureIds";
import type { ScenarioJson } from "@/types/scenario";

/** Typed view of the loadable VAMOS FOUR departure sample. */
export const quickstartDepartureSample =
  quickstartDepartureSampleJson as ScenarioJson;

function readSampleProcedureIds(): RjttCatalogProcedureId[] {
  return quickstartDepartureSample.aircraft.map((ac) => {
    const id = ac.procedureId;
    if (id == null || !isRjttCatalogProcedureId(id)) {
      throw new Error(
        `quickstart-departure-sample.json procedureId is missing or not in catalog: ${String(id)}`
      );
    }
    if (id === "VISIP1") {
      throw new Error("VISIP must not appear in the departure sample");
    }
    return id;
  });
}

function readSampleTransitionIds(): RjttCatalogProcedureId[] {
  return quickstartDepartureSample.aircraft.map((ac) => {
    const id = ac.transitionId;
    if (id == null || !isRjttCatalogProcedureId(id)) {
      throw new Error(
        `quickstart-departure-sample.json transitionId is missing or not in catalog: ${String(id)}`
      );
    }
    return id;
  });
}

export const QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS =
  readSampleProcedureIds();
export const QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS =
  readSampleTransitionIds();

const _procedureSatisfiesCatalog: readonly RjttCatalogProcedureId[] =
  QUICKSTART_DEPARTURE_PROCEDURE_IDS;
void _procedureSatisfiesCatalog;

const _transitionSatisfiesCatalog: readonly RjttCatalogProcedureId[] =
  QUICKSTART_DEPARTURE_TRANSITION_IDS;
void _transitionSatisfiesCatalog;

export function assertQuickstartDepartureSampleAgainstCatalog(): void {
  if (
    QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS.length !==
    QUICKSTART_DEPARTURE_PROCEDURE_IDS.length
  ) {
    throw new Error(
      `Expected ${QUICKSTART_DEPARTURE_PROCEDURE_IDS.length} departure procedureIds, got ${QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS.length}`
    );
  }
  for (let i = 0; i < QUICKSTART_DEPARTURE_PROCEDURE_IDS.length; i++) {
    if (
      QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS[i] !==
      QUICKSTART_DEPARTURE_PROCEDURE_IDS[i]
    ) {
      throw new Error(
        `departure procedureId mismatch at ${i}: expected ${QUICKSTART_DEPARTURE_PROCEDURE_IDS[i]}, got ${QUICKSTART_DEPARTURE_SAMPLE_PROCEDURE_IDS[i]}`
      );
    }
  }
  for (let i = 0; i < QUICKSTART_DEPARTURE_TRANSITION_IDS.length; i++) {
    if (
      QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS[i] !==
      QUICKSTART_DEPARTURE_TRANSITION_IDS[i]
    ) {
      throw new Error(
        `departure transitionId mismatch at ${i}: expected ${QUICKSTART_DEPARTURE_TRANSITION_IDS[i]}, got ${QUICKSTART_DEPARTURE_SAMPLE_TRANSITION_IDS[i]}`
      );
    }
  }
  const atis = quickstartDepartureSample.atis;
  if (
    atis == null ||
    atis.length !== 1 ||
    atis[0]?.icao !== "RJTT" ||
    atis[0]?.runwayInUse !== "34L"
  ) {
    throw new Error("departure sample must seed RJTT runwayInUse 34L");
  }
}
