"use client";

import React, { useEffect } from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { fetchAircraftLocation } from "@/utility/api/location";
import { Aircraft } from "@/utility/aircraft/aircraftClass";
import type { DisplayRange } from "@/context/displayRangeContext";
import FlightPlanDisplay from "@/components/flightPlanDisplay";
import {
  SelectedAircraftProvider,
  useSelectedAircraft,
} from "@/context/selectedAircraftContext";
import * as flightPlanApi from "@/utility/api/flightPlan";

jest.mock("@/utility/api/flightPlan");

/**
 * Inverted locationAndPanel repro (P-1 / P-4 / PR-D).
 */

const center = { latitude: 35.0, longitude: 139.0 };
const range: DisplayRange = { range: 400 };

function seedAircraft(callsign: string): Aircraft {
  const ac = new Aircraft(
    callsign,
    { x: 10, y: 20, altitude: 5000 },
    { latitude: 35.1, longitude: 139.3 },
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
  ac.labelPinned = true;
  return ac;
}

function StateSetter({ callsign }: { callsign: string | null }) {
  const { setCallsign } = useSelectedAircraft();
  useEffect(() => {
    setCallsign(callsign);
  }, [callsign, setCallsign]);
  return null;
}

describe("locationAndPanel repro (inverted PR-D)", () => {
  afterEach(() => {
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  it("P-1: HTTP 500 yields { kind: error }, not []", async () => {
    global.fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 500,
    }) as unknown as typeof fetch;

    const result = await fetchAircraftLocation(
      [seedAircraft("KEEP1")],
      center,
      range
    );
    expect(result).toEqual({ kind: "error" });
    expect(result).not.toEqual([]);
  });

  it("P-1: network exception yields { kind: error }, not []", async () => {
    global.fetch = jest
      .fn()
      .mockRejectedValue(new Error("network down")) as unknown as typeof fetch;

    const result = await fetchAircraftLocation(
      [seedAircraft("KEEP1")],
      center,
      range
    );
    expect(result).toEqual({ kind: "error" });
  });

  it("P-4: delayed A refresh after switching to B does not revert panel to AFIXX", async () => {
    let resolveA: ((value: flightPlanApi.FlightPlanStatus) => void) | null =
      null;

    (flightPlanApi.fetchFlightPlan as jest.Mock).mockImplementation(
      (cs: string) => {
        if (cs === "AFIXX") {
          return new Promise<flightPlanApi.FlightPlanStatus>((resolve) => {
            resolveA = resolve;
          });
        }
        return Promise.resolve({
          callsign: "BFIXX",
          navigationMode: "DIRECT",
          remainingWaypoints: ["BFIX"],
          hasFlightPlan: true,
          currentWaypoint: "BFIX",
        });
      }
    );

    const { rerender } = render(
      <SelectedAircraftProvider>
        <StateSetter callsign="AFIXX" />
        <FlightPlanDisplay />
      </SelectedAircraftProvider>
    );

    await act(async () => {
      await Promise.resolve();
    });

    rerender(
      <SelectedAircraftProvider>
        <StateSetter callsign="BFIXX" />
        <FlightPlanDisplay />
      </SelectedAircraftProvider>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/BFIX/).length).toBeGreaterThan(0);
    });

    await act(async () => {
      resolveA?.({
        callsign: "AFIXX",
        navigationMode: "DIRECT",
        remainingWaypoints: ["AFIX"],
        hasFlightPlan: true,
        currentWaypoint: "AFIX",
      });
      await Promise.resolve();
    });

    expect(screen.getAllByText(/BFIX/).length).toBeGreaterThan(0);
    expect(screen.queryByText(/AFIX/)).not.toBeInTheDocument();
    expect(screen.queryByText(/AFIXX/)).not.toBeInTheDocument();
  });
});
