/**
 * Automatic data-block / leader-line offsets (8-compass, fixed length).
 * Client-only; does not touch the location API. Spec:
 * spec/20261122-leader-line-auto-placement/spec.md
 */

import {
  AIRCRAFT_SYMBOL_RADIUS_PX,
  DATA_BLOCK_HIT_BOTTOM_SLACK_PX,
  DATA_BLOCK_HIT_LEFT_PAD_PX,
  DATA_BLOCK_HIT_TOP_PAD_PX,
  DATA_BLOCK_HIT_WIDTH_PX,
  DATA_BLOCK_LINE_HEIGHT_PX,
} from "./drawAircraft";

/** Leader length for cardinal offsets; diagonals keep the same length. */
export const LABEL_LEADER_LENGTH_PX = 50;

/** Minimum data-block line count used when display settings are unavailable. */
export const DEFAULT_DATA_BLOCK_LINE_COUNT = 4;

/** Hit extent below the first baseline for the default 4-line block. */
export const DEFAULT_LABEL_HIT_BOTTOM_OFFSET_PX =
  DEFAULT_DATA_BLOCK_LINE_COUNT * DATA_BLOCK_LINE_HEIGHT_PX +
  DATA_BLOCK_HIT_BOTTOM_SLACK_PX;

export const LABEL_DIRECTIONS = [
  "N",
  "NE",
  "E",
  "SE",
  "S",
  "SW",
  "W",
  "NW",
] as const;

export type LabelDirection = (typeof LABEL_DIRECTIONS)[number];

export type LabelOffset = { readonly x: number; readonly y: number };

export type CanvasBounds = {
  readonly width: number;
  readonly height: number;
};

export type LabelPlacementAircraft = {
  readonly callsign: string;
  position: { x: number; y: number };
  label: { x: number; y: number };
  labelPinned: boolean;
  /** Hit extent below the first label baseline (matches DrawAircraft). */
  readonly hitBottomOffsetPx: number;
};

export type ComputeLabelPlacementsOptions = {
  readonly canvas: CanvasBounds;
  /** Callsigns that must receive a fresh 8-direction assignment. */
  readonly forcePlaceCallsigns?: ReadonlySet<string>;
  readonly symbolRadiusPx?: number;
};

export type Rect = {
  readonly left: number;
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
};

const DIAGONAL_COMPONENT_PX = Math.round(LABEL_LEADER_LENGTH_PX / Math.SQRT2);

const DIRECTION_OFFSETS: Record<LabelDirection, LabelOffset> = {
  N: { x: 0, y: LABEL_LEADER_LENGTH_PX },
  NE: { x: DIAGONAL_COMPONENT_PX, y: DIAGONAL_COMPONENT_PX },
  E: { x: LABEL_LEADER_LENGTH_PX, y: 0 },
  SE: { x: DIAGONAL_COMPONENT_PX, y: -DIAGONAL_COMPONENT_PX },
  S: { x: 0, y: -LABEL_LEADER_LENGTH_PX },
  SW: { x: -DIAGONAL_COMPONENT_PX, y: -DIAGONAL_COMPONENT_PX },
  W: { x: -LABEL_LEADER_LENGTH_PX, y: 0 },
  NW: { x: -DIAGONAL_COMPONENT_PX, y: DIAGONAL_COMPONENT_PX },
};

export function offsetForDirection(direction: LabelDirection): LabelOffset {
  return DIRECTION_OFFSETS[direction];
}

/** Anchor of the first data-block baseline (same as drawAircraft). */
export function labelAnchor(
  position: { readonly x: number; readonly y: number },
  label: LabelOffset
): { x: number; y: number } {
  return {
    x: position.x + label.x,
    y: position.y - label.y,
  };
}

export function labelHitRect(
  position: { readonly x: number; readonly y: number },
  label: LabelOffset,
  hitBottomOffsetPx: number,
  hitWidthPx: number = DATA_BLOCK_HIT_WIDTH_PX,
  hitTopPadPx: number = DATA_BLOCK_HIT_TOP_PAD_PX,
  hitLeftPadPx: number = DATA_BLOCK_HIT_LEFT_PAD_PX
): Rect {
  const anchor = labelAnchor(position, label);
  return {
    left: anchor.x - hitLeftPadPx,
    top: anchor.y - hitTopPadPx,
    right: anchor.x + hitWidthPx,
    bottom: anchor.y + hitBottomOffsetPx,
  };
}

export function symbolHitRect(
  position: { readonly x: number; readonly y: number },
  radiusPx: number = AIRCRAFT_SYMBOL_RADIUS_PX
): Rect {
  return {
    left: position.x - radiusPx,
    top: position.y - radiusPx,
    right: position.x + radiusPx,
    bottom: position.y + radiusPx,
  };
}

export function rectIntersectionArea(a: Rect, b: Rect): number {
  const w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
  const h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
  if (w <= 0 || h <= 0) {
    return 0;
  }
  return w * h;
}

export function rectsOverlap(a: Rect, b: Rect): boolean {
  return rectIntersectionArea(a, b) > 0;
}

/** Area of `rect` that lies outside the canvas (0 when fully inside). */
export function rectOutsideCanvasArea(
  rect: Rect,
  canvas: CanvasBounds
): number {
  const full =
    Math.max(0, rect.right - rect.left) * Math.max(0, rect.bottom - rect.top);
  const clippedLeft = Math.max(rect.left, 0);
  const clippedTop = Math.max(rect.top, 0);
  const clippedRight = Math.min(rect.right, canvas.width);
  const clippedBottom = Math.min(rect.bottom, canvas.height);
  const insideW = Math.max(0, clippedRight - clippedLeft);
  const insideH = Math.max(0, clippedBottom - clippedTop);
  return full - insideW * insideH;
}

export function isLabelPlacementValid(
  position: { readonly x: number; readonly y: number },
  label: LabelOffset,
  hitBottomOffsetPx: number,
  obstacles: readonly Rect[],
  canvas: CanvasBounds
): boolean {
  const rect = labelHitRect(position, label, hitBottomOffsetPx);
  if (rectOutsideCanvasArea(rect, canvas) > 0) {
    return false;
  }
  for (const obstacle of obstacles) {
    if (rectsOverlap(rect, obstacle)) {
      return false;
    }
  }
  return true;
}

function scoreLabelOffset(
  position: { readonly x: number; readonly y: number },
  label: LabelOffset,
  hitBottomOffsetPx: number,
  obstacles: readonly Rect[],
  canvas: CanvasBounds
): { overlapArea: number; outsideArea: number } {
  const rect = labelHitRect(position, label, hitBottomOffsetPx);
  let overlapArea = 0;
  for (const obstacle of obstacles) {
    overlapArea += rectIntersectionArea(rect, obstacle);
  }
  return {
    overlapArea,
    outsideArea: rectOutsideCanvasArea(rect, canvas),
  };
}

/**
 * Pick the first 8-compass offset with zero overlap and fully on-canvas;
 * otherwise the offset with the smallest (outside + overlap) cost (ties → direction order).
 */
export function chooseLabelOffset(
  position: { readonly x: number; readonly y: number },
  hitBottomOffsetPx: number,
  obstacles: readonly Rect[],
  canvas: CanvasBounds
): LabelOffset {
  let best: LabelOffset = offsetForDirection(LABEL_DIRECTIONS[0]);
  let bestOverlap = Number.POSITIVE_INFINITY;
  let bestOutside = Number.POSITIVE_INFINITY;

  for (const direction of LABEL_DIRECTIONS) {
    const candidate = offsetForDirection(direction);
    const { overlapArea, outsideArea } = scoreLabelOffset(
      position,
      candidate,
      hitBottomOffsetPx,
      obstacles,
      canvas
    );
    if (overlapArea === 0 && outsideArea === 0) {
      return candidate;
    }
    if (
      outsideArea < bestOutside ||
      (outsideArea === bestOutside && overlapArea < bestOverlap)
    ) {
      best = candidate;
      bestOverlap = overlapArea;
      bestOutside = outsideArea;
    }
  }

  return best;
}

function compareCallsign(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * Assign / repair label offsets. Pinned labels never move.
 * Unpinned labels keep their offset unless invalid or listed in `forcePlaceCallsigns`.
 * Mutates `label` on each aircraft; deterministic for the same inputs.
 */
export function computeLabelPlacements(
  aircraft: readonly LabelPlacementAircraft[],
  options: ComputeLabelPlacementsOptions
): void {
  const { canvas, forcePlaceCallsigns } = options;
  const symbolRadiusPx = options.symbolRadiusPx ?? AIRCRAFT_SYMBOL_RADIUS_PX;
  const ordered = [...aircraft].sort((a, b) =>
    compareCallsign(a.callsign, b.callsign)
  );

  const placedLabelRects: Rect[] = [];

  for (const current of ordered) {
    const otherSymbolObstacles = ordered
      .filter((other) => other.callsign !== current.callsign)
      .map((other) => symbolHitRect(other.position, symbolRadiusPx));

    const obstacles = [...placedLabelRects, ...otherSymbolObstacles];

    if (current.labelPinned) {
      placedLabelRects.push(
        labelHitRect(current.position, current.label, current.hitBottomOffsetPx)
      );
      continue;
    }

    const force =
      forcePlaceCallsigns != null && forcePlaceCallsigns.has(current.callsign);
    const keepCurrent =
      !force &&
      isLabelPlacementValid(
        current.position,
        current.label,
        current.hitBottomOffsetPx,
        obstacles,
        canvas
      );

    if (!keepCurrent) {
      const next = chooseLabelOffset(
        current.position,
        current.hitBottomOffsetPx,
        obstacles,
        canvas
      );
      current.label.x = next.x;
      current.label.y = next.y;
    }

    placedLabelRects.push(
      labelHitRect(current.position, current.label, current.hitBottomOffsetPx)
    );
  }
}
