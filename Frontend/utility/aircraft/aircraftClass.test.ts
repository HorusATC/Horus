import { Aircraft, POSITION_HISTORY_MAX_POINTS } from "./aircraftClass";

describe("Aircraft", () => {
  const basePosition = { x: 100, y: 200, altitude: 35000 };
  const baseGeo = { latitude: 35.0, longitude: 139.0 };
  const baseVector = {
    heading: 90,
    groundSpeed: 250,
    verticalSpeed: 0,
  };
  const baseInstructedVector = {
    heading: 90,
    groundSpeed: 250,
    altitude: 35000,
  };

  it("applyPolledFields syncs instructedVector from polled location (Controller path)", () => {
    // Regression: Controller polls used to skip instructedVector, so the
    // data-block target altitude did not move after Operator altitude
    // instructions (PR #119 / main).
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );

    const newAircraft = new Aircraft(
      "JAL123",
      { x: 150, y: 250, altitude: 36000 },
      { latitude: 35.1, longitude: 139.1 },
      { heading: 95, groundSpeed: 260, verticalSpeed: 5 },
      { heading: 180, groundSpeed: 300, altitude: 39000 },
      { heading: 200, groundSpeed: 240, altitude: 34000 },
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      "7600",
      "12:05",
      50,
      50,
      30
    );

    aircraft.applyPolledFields(newAircraft);

    expect(aircraft.position).toEqual(newAircraft.position);
    expect(aircraft.vector).toEqual(newAircraft.vector);
    expect(aircraft.instructedVector).toEqual(newAircraft.instructedVector);
    expect(aircraft.eta).toBe("12:05");
    expect(aircraft.riskLevel).toBe(30);
    expect(aircraft.atcClearance).toEqual({
      heading: 200,
      groundSpeed: 240,
      altitude: 34000,
    });
    expect(aircraft.label).toEqual({ x: 50, y: 50 });
    expect(aircraft.labelPinned).toBe(false);
    expect(aircraft.positionHistory).toHaveLength(1);
    const historyEntry = aircraft.positionHistory[0];
    expect(historyEntry).toBeDefined();
    if (!historyEntry) {
      throw new Error("expected positionHistory[0]");
    }
    expect(historyEntry).toMatchObject({
      latitude: baseGeo.latitude,
      longitude: baseGeo.longitude,
      altitude: basePosition.altitude,
    });
    expect(historyEntry.recordedAt).toEqual(expect.any(String));
  });

  it("applyPolledFields does not overwrite labelPinned or label", () => {
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      12,
      -34,
      0,
      "HEADING",
      null,
      false,
      false,
      null,
      null,
      true
    );
    const next = new Aircraft(
      "JAL123",
      { x: 150, y: 250, altitude: 36000 },
      { latitude: 35.1, longitude: 139.1 },
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:05",
      50,
      50,
      0
    );
    aircraft.applyPolledFields(next);
    expect(aircraft.labelPinned).toBe(true);
    expect(aircraft.label).toEqual({ x: 12, y: -34 });
  });

  it("applyPolledFields copies holding pattern and clears it when null", () => {
    const holding = {
      fixName: "ABENO",
      fixLatitude: 35.1,
      fixLongitude: 139.0,
      inboundCourseDeg: 90,
      turnDirection: "RIGHT",
      legMinutes: 1,
      outboundLegNm: 3,
      turnRateDegPerSec: 3,
      turnRadiusNm: 0.95,
      pattern: [
        { latitude: 35.1, longitude: 139.0 },
        { latitude: 35.0, longitude: 139.0 },
        { latitude: 35.1, longitude: 139.0 },
      ],
    };
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0,
      "HOLDING",
      null,
      false,
      false,
      null,
      holding
    );
    const cleared = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0,
      "HEADING",
      null,
      false,
      false,
      null,
      null
    );
    expect(aircraft.holding?.fixName).toBe("ABENO");
    aircraft.applyPolledFields(cleared);
    expect(aircraft.holding).toBeNull();
    expect(aircraft.navigationMode).toBe("HEADING");
  });

  it("applyPolledFields copies goAround", () => {
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );
    const next = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0,
      "HEADING",
      null,
      true
    );
    aircraft.applyPolledFields(next);
    expect(aircraft.goAround).toBe(true);
  });

  it("applyPolledFields copies towerHandoff", () => {
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );
    const next = new Aircraft(
      "JAL123",
      { x: 120, y: 240, altitude: 3000 },
      { latitude: 35.2, longitude: 139.2 },
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:05",
      50,
      50,
      0,
      "HEADING",
      null,
      false,
      true
    );
    aircraft.applyPolledFields(next);
    expect(aircraft.towerHandoff).toBe(true);
  });

  it("applyPolledFields does not overwrite identity fields when source differs", () => {
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );
    const next = new Aircraft(
      "JAL123",
      { x: 150, y: 250, altitude: 36000 },
      { latitude: 35.1, longitude: 139.1 },
      { heading: 95, groundSpeed: 260, verticalSpeed: 5 },
      { heading: 180, groundSpeed: 300, altitude: 39000 },
      null,
      "Military",
      "F-15",
      "OKA",
      "ROAH",
      "CTS",
      "RJCC",
      "7700",
      "12:05",
      50,
      50,
      10
    );

    aircraft.applyPolledFields(next);

    expect(aircraft.type).toBe("Commercial");
    expect(aircraft.model).toBe("B738");
    expect(aircraft.originIata).toBe("HND");
    expect(aircraft.originIcao).toBe("RJTT");
    expect(aircraft.destinationIata).toBe("NRT");
    expect(aircraft.destinationIcao).toBe("RJAA");
    expect(aircraft.position).toEqual(next.position);
    expect(aircraft.squawk).toBe("7700");
  });

  it("caps position history at POSITION_HISTORY_MAX_POINTS", () => {
    const aircraft = new Aircraft(
      "JAL999",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );

    for (let i = 0; i < POSITION_HISTORY_MAX_POINTS + 5; i += 1) {
      const next = new Aircraft(
        "JAL999",
        { x: 100 + i, y: 200, altitude: 35000 },
        { latitude: 35.0 + i * 0.001, longitude: 139.0 },
        baseVector,
        baseInstructedVector,
        null,
        "Commercial",
        "B738",
        "HND",
        "RJTT",
        "NRT",
        "RJAA",
        null,
        "12:00",
        50,
        50,
        0
      );
      aircraft.applyPolledFields(next);
    }

    expect(aircraft.positionHistory.length).toBe(POSITION_HISTORY_MAX_POINTS);
  });

  it("applyPolledFields refreshes optional ground track on vector", () => {
    const aircraft = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      baseVector,
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );
    const polled = new Aircraft(
      "JAL123",
      basePosition,
      baseGeo,
      { heading: 90, groundSpeed: 250, verticalSpeed: 0, track: 95 },
      baseInstructedVector,
      null,
      "Commercial",
      "B738",
      "HND",
      "RJTT",
      "NRT",
      "RJAA",
      null,
      "12:00",
      50,
      50,
      0
    );
    aircraft.applyPolledFields(polled);
    expect(aircraft.vector.track).toBe(95);
    expect(aircraft.vector.heading).toBe(90);
  });
});
