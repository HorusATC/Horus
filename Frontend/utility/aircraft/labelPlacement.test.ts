import {
  chooseLabelOffset,
  computeLabelPlacements,
  DEFAULT_LABEL_HIT_BOTTOM_OFFSET_PX,
  isLabelPlacementValid,
  LABEL_DIRECTIONS,
  LABEL_LEADER_LENGTH_PX,
  labelHitRect,
  offsetForDirection,
  rectIntersectionArea,
  rectOutsideCanvasArea,
  type LabelPlacementAircraft,
} from "./labelPlacement";

const CANVAS = { width: 1000, height: 1000 } as const;
const HIT = DEFAULT_LABEL_HIT_BOTTOM_OFFSET_PX;

function aircraft(
  callsign: string,
  x: number,
  y: number,
  label = { x: 50, y: 50 },
  labelPinned = false
): LabelPlacementAircraft {
  return {
    callsign,
    position: { x, y },
    label: { ...label },
    labelPinned,
    hitBottomOffsetPx: HIT,
  };
}

describe("offsetForDirection", () => {
  it("uses leader length 50 on cardinals and equal components on diagonals", () => {
    expect(offsetForDirection("N")).toEqual({
      x: 0,
      y: LABEL_LEADER_LENGTH_PX,
    });
    expect(offsetForDirection("E")).toEqual({
      x: LABEL_LEADER_LENGTH_PX,
      y: 0,
    });
    const ne = offsetForDirection("NE");
    expect(ne.x).toBe(ne.y);
    expect(ne.x).toBe(Math.round(LABEL_LEADER_LENGTH_PX / Math.SQRT2));
    expect(LABEL_DIRECTIONS).toHaveLength(8);
  });
});

describe("computeLabelPlacements", () => {
  it("assigns non-overlapping 8-direction offsets to two coincident aircraft (callsign order)", () => {
    const a = aircraft("AAA001", 400, 400);
    const b = aircraft("BBB001", 400, 400);
    computeLabelPlacements([b, a], {
      canvas: CANVAS,
      forcePlaceCallsigns: new Set(["AAA001", "BBB001"]),
    });

    expect(a.label).not.toEqual(b.label);
    const area = rectIntersectionArea(
      labelHitRect(a.position, a.label, HIT),
      labelHitRect(b.position, b.label, HIT)
    );
    expect(area).toBe(0);
    expect(
      LABEL_DIRECTIONS.some((d) => {
        const o = offsetForDirection(d);
        return o.x === a.label.x && o.y === a.label.y;
      })
    ).toBe(true);
  });

  it("keeps intersection area 0 for three spaced aircraft when 8-directions suffice", () => {
    const fleet = [
      aircraft("A1", 300, 500),
      aircraft("A2", 500, 500),
      aircraft("A3", 700, 500),
    ];
    computeLabelPlacements(fleet, {
      canvas: CANVAS,
      forcePlaceCallsigns: new Set(fleet.map((f) => f.callsign)),
    });
    for (let i = 0; i < fleet.length; i += 1) {
      for (let j = i + 1; j < fleet.length; j += 1) {
        const left = fleet[i];
        const right = fleet[j];
        expect(left).toBeDefined();
        expect(right).toBeDefined();
        if (!left || !right) continue;
        expect(
          rectIntersectionArea(
            labelHitRect(left.position, left.label, HIT),
            labelHitRect(right.position, right.label, HIT)
          )
        ).toBe(0);
      }
    }
  });

  it("does not move a pinned label during reassignment", () => {
    const pinned = aircraft("PIN001", 400, 400, { x: 12, y: -34 }, true);
    const other = aircraft("ZZZ999", 400, 400, { x: 50, y: 50 }, false);
    computeLabelPlacements([pinned, other], {
      canvas: CANVAS,
      // Even a forced reassignment must leave pinned offsets untouched.
      forcePlaceCallsigns: new Set(["PIN001", "ZZZ999"]),
    });
    expect(pinned.label).toEqual({ x: 12, y: -34 });
    expect(other.label).not.toEqual({ x: 12, y: -34 });
  });

  it("treats a later-sorted pinned label as an obstacle for earlier unpinned", () => {
    // Without seeding pinned rects first, AAA001 would take NE and overlap N
    // (intersection area 2800) because ZZZ999 is not yet in placedLabelRects.
    const unpinned = aircraft("AAA001", 400, 400, { x: 50, y: 50 }, false);
    const pinned = aircraft("ZZZ999", 400, 400, offsetForDirection("N"), true);
    computeLabelPlacements([unpinned, pinned], {
      canvas: CANVAS,
      forcePlaceCallsigns: new Set(["AAA001"]),
    });
    expect(pinned.label).toEqual(offsetForDirection("N"));
    expect(
      rectIntersectionArea(
        labelHitRect(unpinned.position, unpinned.label, HIT),
        labelHitRect(pinned.position, pinned.label, HIT)
      )
    ).toBe(0);
    expect(unpinned.label).not.toEqual(offsetForDirection("NE"));
  });

  it("is deterministic for the same positions and callsigns", () => {
    const make = () => [
      aircraft("JAL100", 300, 300),
      aircraft("ANA200", 310, 305),
      aircraft("UAL300", 320, 290),
    ];
    const first = make();
    const second = make();
    const force = new Set(["ANA200", "JAL100", "UAL300"]);
    computeLabelPlacements(first, {
      canvas: CANVAS,
      forcePlaceCallsigns: force,
    });
    computeLabelPlacements(second, {
      canvas: CANVAS,
      forcePlaceCallsigns: force,
    });
    expect(first.map((f) => ({ c: f.callsign, ...f.label }))).toEqual(
      second.map((f) => ({ c: f.callsign, ...f.label }))
    );
  });

  it("does not jitter when inputs are unchanged and labels are already valid", () => {
    const fleet = [aircraft("A", 200, 200), aircraft("B", 600, 600)];
    computeLabelPlacements(fleet, {
      canvas: CANVAS,
      forcePlaceCallsigns: new Set(["A", "B"]),
    });
    const snapshot = fleet.map((f) => ({ ...f.label }));
    computeLabelPlacements(fleet, { canvas: CANVAS });
    computeLabelPlacements(fleet, { canvas: CANVAS });
    expect(fleet.map((f) => ({ ...f.label }))).toEqual(snapshot);
  });

  it("repairs an unpinned label that sits off-canvas", () => {
    const a = aircraft("EDGE1", 50, 50, { x: -80, y: 80 });
    expect(
      rectOutsideCanvasArea(labelHitRect(a.position, a.label, HIT), CANVAS)
    ).toBeGreaterThan(0);
    computeLabelPlacements([a], { canvas: CANVAS });
    expect(
      rectOutsideCanvasArea(labelHitRect(a.position, a.label, HIT), CANVAS)
    ).toBe(0);
  });

  it("repairs unpinned labels that overlap after aircraft converge", () => {
    const a = aircraft("C1", 400, 400, offsetForDirection("N"));
    const b = aircraft("C2", 700, 400, offsetForDirection("N"));
    computeLabelPlacements([a, b], { canvas: CANVAS });
    expect(a.label).toEqual(offsetForDirection("N"));
    expect(b.label).toEqual(offsetForDirection("N"));

    a.position = { x: 500, y: 500 };
    b.position = { x: 500, y: 500 };
    computeLabelPlacements([a, b], { canvas: CANVAS });
    expect(
      rectIntersectionArea(
        labelHitRect(a.position, a.label, HIT),
        labelHitRect(b.position, b.label, HIT)
      )
    ).toBe(0);
  });
});

describe("chooseLabelOffset / validity", () => {
  it("prefers the first free direction in fixed order", () => {
    const position = { x: 500, y: 500 };
    const blockingN = labelHitRect(position, offsetForDirection("N"), HIT);
    const chosen = chooseLabelOffset(position, HIT, [blockingN], CANVAS);
    // NE and E still intersect the N hit box at this size; SE is first clear.
    expect(chosen).toEqual(offsetForDirection("SE"));
    expect(
      isLabelPlacementValid(position, chosen, HIT, [blockingN], CANVAS)
    ).toBe(true);
  });
});
