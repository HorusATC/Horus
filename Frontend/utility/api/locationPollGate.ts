import type { Aircraft } from "../aircraft/aircraftClass";
import type { FetchResult } from "./fetchResult";

/** In-flight guard + monotonic seq for location polling (P-2). */
export type LocationPollGate = {
  inFlight: boolean;
  nextSeq: number;
  lastAppliedSeq: number;
};

export function createLocationPollGate(): LocationPollGate {
  return { inFlight: false, nextSeq: 0, lastAppliedSeq: 0 };
}

/** Returns the seq to attach, or null when a request is already in flight. */
export function beginLocationPoll(gate: LocationPollGate): number | null {
  if (gate.inFlight) {
    return null;
  }
  gate.inFlight = true;
  gate.nextSeq += 1;
  return gate.nextSeq;
}

/**
 * Clears the in-flight flag. Applies fleet only for ok results newer than
 * lastAppliedSeq; returns the fleet to apply, or null to keep the previous list.
 */
export function finishLocationPoll(
  gate: LocationPollGate,
  seq: number,
  result: FetchResult<Aircraft[]>
): Aircraft[] | null {
  gate.inFlight = false;
  if (result.kind !== "ok") {
    return null;
  }
  if (seq <= gate.lastAppliedSeq) {
    return null;
  }
  gate.lastAppliedSeq = seq;
  return result.data;
}
