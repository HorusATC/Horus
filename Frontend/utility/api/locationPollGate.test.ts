import { Aircraft } from "@/utility/aircraft/aircraftClass";
import {
  beginLocationPoll,
  createLocationPollGate,
  finishLocationPoll,
} from "./locationPollGate";

function seed(callsign: string, lat: number): Aircraft {
  return new Aircraft(
    callsign,
    { x: 10, y: 20, altitude: 5000 },
    { latitude: lat, longitude: 139.3 },
    { heading: 180, groundSpeed: 200, verticalSpeed: 0 },
    { heading: 180, groundSpeed: 200, altitude: 5000 },
    null,
    "Commercial",
    "A320",
    "HND",
    "RJTT",
    "NRT",
    "RJAA",
    null,
    "14:30",
    50,
    50,
    0
  );
}

describe("location poll combination (P-2 / error keep)", () => {
  it("out-of-order responses keep only the newer fleet", () => {
    const gate = createLocationPollGate();
    const seq1 = beginLocationPoll(gate);
    expect(seq1).toBe(1);
    gate.inFlight = false;
    const seq2 = beginLocationPoll(gate);
    expect(seq2).toBe(2);

    const newer = [seed("NEW", 35.2)];
    const older = [seed("OLD", 35.1)];

    const appliedNew = finishLocationPoll(gate, seq2!, {
      kind: "ok",
      data: newer,
    });
    expect(appliedNew).toBe(newer);

    gate.inFlight = true;
    const appliedOld = finishLocationPoll(gate, seq1!, {
      kind: "ok",
      data: older,
    });
    expect(appliedOld).toBeNull();
  });

  it("error between successes does not clear the fleet", () => {
    const gate = createLocationPollGate();
    const seqOk = beginLocationPoll(gate);
    const fleet = [seed("KEEP", 35.1)];
    fleet[0]!.labelPinned = true;
    expect(finishLocationPoll(gate, seqOk!, { kind: "ok", data: fleet })).toBe(
      fleet
    );

    const seqErr = beginLocationPoll(gate);
    expect(finishLocationPoll(gate, seqErr!, { kind: "error" })).toBeNull();

    const seqOk2 = beginLocationPoll(gate);
    const next = [seed("KEEP", 35.15)];
    next[0]!.labelPinned = true;
    expect(finishLocationPoll(gate, seqOk2!, { kind: "ok", data: next })).toBe(
      next
    );
  });

  it("skips starting a new request while in flight", () => {
    const gate = createLocationPollGate();
    expect(beginLocationPoll(gate)).toBe(1);
    expect(beginLocationPoll(gate)).toBeNull();
  });
});
