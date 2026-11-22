import { DrawAircraft } from "./drawAircraft";
import { Aircraft } from "./aircraftClass";
import type { DataBlockDisplaySetting } from "@/context/dataBlockDisplaySettingContext";
import { GLOBAL_SETTINGS } from "../globals/settings";
import CoordinateManager from "../coordinateManager/CoordinateManager";

/**
 * Inverted drawRoute repro (⑨ / PR-D): polyline starts at the aircraft
 * geoPosition and draws when ≥1 remaining fix remains.
 */

function createMockContext() {
  return {
    save: jest.fn(),
    restore: jest.fn(),
    beginPath: jest.fn(),
    moveTo: jest.fn(),
    lineTo: jest.fn(),
    stroke: jest.fn(),
    fill: jest.fn(),
    fillText: jest.fn(),
    arc: jest.fn(),
    setLineDash: jest.fn(),
    measureText: jest.fn(() => ({ width: 40 })),
    strokeStyle: "",
    fillStyle: "",
    globalAlpha: 1,
    lineWidth: 1,
    font: "",
  } as unknown as CanvasRenderingContext2D & {
    moveTo: jest.Mock;
    lineTo: jest.Mock;
    stroke: jest.Mock;
    beginPath: jest.Mock;
  };
}

const center = { latitude: 35.55, longitude: 139.78 };
const displayRange = { range: 80 };
const dataBlock: DataBlockDisplaySetting = {
  squawk: false,
  aircraftType: true,
  eta: true,
  atcClearanceMemo: false,
};

function aircraftWithRoute(
  route: Array<{ fix: string; latitude: number; longitude: number }> | null
): Aircraft {
  return new Aircraft(
    "RTE01",
    { x: 500, y: 500, altitude: 3000 },
    { latitude: 35.6, longitude: 139.75 },
    { heading: 150, groundSpeed: 180, verticalSpeed: 0 },
    { heading: 150, groundSpeed: 180, altitude: 3000 },
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
    0,
    "DIRECT",
    null,
    false,
    false,
    false,
    null,
    null,
    route
  );
}

function strokeStylesUsed(
  draw: (ctx: CanvasRenderingContext2D) => void
): string[] {
  const styles: string[] = [];
  const ctx = createMockContext();
  Object.defineProperty(ctx, "strokeStyle", {
    set(v: string) {
      styles.push(String(v));
    },
    get() {
      return styles[styles.length - 1] ?? "";
    },
  });
  draw(ctx);
  return styles;
}

describe("drawRoute repro (inverted PR-D / ⑨)", () => {
  beforeEach(() => {
    GLOBAL_SETTINGS.canvasWidth = 1000;
    GLOBAL_SETTINGS.canvasHeight = 1000;
  });

  it("remaining 2 fixes: aircraft→first and first→second (stroke; not only from first fix)", () => {
    const ctx = createMockContext();
    const route = [
      { fix: "FIXA", latitude: 35.58, longitude: 139.76 },
      { fix: "FIXB", latitude: 35.56, longitude: 139.77 },
    ];
    const ac = aircraftWithRoute(route);
    DrawAircraft.drawAircraft(ctx, ac, displayRange, center, dataBlock);

    expect(
      strokeStylesUsed((c) =>
        DrawAircraft.drawAircraft(c, ac, displayRange, center, dataBlock)
      )
    ).toContain("#22c55e");

    const aircraftCanvas = CoordinateManager.calculateCanvasCoordinates(
      ac.geoPosition,
      center,
      displayRange
    );
    const fixA = CoordinateManager.calculateCanvasCoordinates(
      { latitude: route[0]!.latitude, longitude: route[0]!.longitude },
      center,
      displayRange
    );
    const fixB = CoordinateManager.calculateCanvasCoordinates(
      { latitude: route[1]!.latitude, longitude: route[1]!.longitude },
      center,
      displayRange
    );

    expect(ctx.moveTo).toHaveBeenCalledWith(aircraftCanvas.x, aircraftCanvas.y);
    expect(ctx.lineTo).toHaveBeenCalledWith(fixA.x, fixA.y);
    expect(ctx.lineTo).toHaveBeenCalledWith(fixB.x, fixB.y);
    expect(ctx.stroke).toHaveBeenCalled();
  });

  it("remaining 1 fix: still strokes aircraft→that fix", () => {
    const ctx = createMockContext();
    const route = [{ fix: "LAST", latitude: 35.58, longitude: 139.76 }];
    const ac = aircraftWithRoute(route);
    DrawAircraft.drawAircraft(ctx, ac, displayRange, center, dataBlock);

    const aircraftCanvas = CoordinateManager.calculateCanvasCoordinates(
      ac.geoPosition,
      center,
      displayRange
    );
    const fix = CoordinateManager.calculateCanvasCoordinates(
      { latitude: route[0]!.latitude, longitude: route[0]!.longitude },
      center,
      displayRange
    );
    expect(ctx.moveTo).toHaveBeenCalledWith(aircraftCanvas.x, aircraftCanvas.y);
    expect(ctx.lineTo).toHaveBeenCalledWith(fix.x, fix.y);
    expect(ctx.stroke).toHaveBeenCalled();
  });

  it("remaining 0 / null: does not use green route stroke", () => {
    expect(
      strokeStylesUsed((c) =>
        DrawAircraft.drawAircraft(
          c,
          aircraftWithRoute(null),
          displayRange,
          center,
          dataBlock
        )
      )
    ).not.toContain("#22c55e");

    expect(
      strokeStylesUsed((c) =>
        DrawAircraft.drawAircraft(
          c,
          aircraftWithRoute([]),
          displayRange,
          center,
          dataBlock
        )
      )
    ).not.toContain("#22c55e");
  });
});

describe("drawRoute combination: remaining 1 then 0", () => {
  beforeEach(() => {
    GLOBAL_SETTINGS.canvasWidth = 1000;
    GLOBAL_SETTINGS.canvasHeight = 1000;
  });

  it("STAR-like remaining 1 draws; after pass (0) the line disappears", () => {
    const one = [{ fix: "STAR1", latitude: 35.58, longitude: 139.76 }];
    expect(
      strokeStylesUsed((c) =>
        DrawAircraft.drawAircraft(
          c,
          aircraftWithRoute(one),
          displayRange,
          center,
          dataBlock
        )
      )
    ).toContain("#22c55e");

    expect(
      strokeStylesUsed((c) =>
        DrawAircraft.drawAircraft(
          c,
          aircraftWithRoute(null),
          displayRange,
          center,
          dataBlock
        )
      )
    ).not.toContain("#22c55e");
  });
});
