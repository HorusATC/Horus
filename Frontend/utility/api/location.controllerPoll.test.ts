import { Aircraft } from "@/utility/aircraft/aircraftClass";
import type { DisplayRange } from "@/context/displayRangeContext";
import { fetchAircraftLocation } from "@/utility/api/location";

/**
 * Regression: location poll must refresh instructedVector (was skipped on the
 * old Controller-only updateAircraftLocationInfo path).
 */
describe("Controller location poll instructedVector", () => {
  const center = { latitude: 35.0, longitude: 139.0 };
  const range: DisplayRange = { range: 400 };

  function seedAircraft(instructedAltitude: number): Aircraft {
    return new Aircraft(
      "ANA404",
      { x: 10, y: 20, altitude: 19000 },
      { latitude: 35.1, longitude: 139.3 },
      { heading: 270, groundSpeed: 290, verticalSpeed: 0 },
      { heading: 270, groundSpeed: 290, altitude: instructedAltitude },
      null,
      "Commercial",
      "A320",
      "KOJ",
      "RJFK",
      "HND",
      "RJTT",
      null,
      "14:30",
      50,
      50,
      0
    );
  }

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it("updates instructedVector altitude/heading/groundSpeed after Operator-style control payload in poll", async () => {
    const existing = [seedAircraft(19000)];

    const dto = {
      callsign: "ANA404",
      position: { latitude: 35.1, longitude: 139.3, altitude: 19100 },
      vector: { heading: 270, groundSpeed: 290, verticalSpeed: 500 },
      instructedVector: {
        heading: 180,
        groundSpeed: 310,
        altitude: 25000,
        indicatedAirspeed: null,
      },
      atcClearance: null,
      type: "COMMERCIAL_PASSENGER",
      model: "A320",
      originIata: "KOJ",
      originIcao: "RJFK",
      destinationIata: "HND",
      destinationIcao: "RJTT",
      eta: "2024-12-13T14:30:00Z",
      squawk: null,
      navigationMode: "HEADING",
      goAround: false,
      towerHandoff: false,
      riskLevel: 0,
      msawAlert: null,
    };

    global.fetch = jest.fn().mockResolvedValue({
      ok: true,
      json: async () => [dto],
    }) as unknown as typeof fetch;

    const updated = await fetchAircraftLocation(existing, center, range);

    expect(updated).toHaveLength(1);
    expect(updated[0].instructedVector.altitude).toBe(25000);
    expect(updated[0].instructedVector.heading).toBe(180);
    expect(updated[0].instructedVector.groundSpeed).toBe(310);
    expect(updated[0].position.altitude).toBe(19100);
  });
});
