import CoordinateManager from "../coordinateManager/CoordinateManager";
import { GLOBAL_CONSTANTS } from "../globals/constants";
import { GLOBAL_SETTINGS } from "../globals/settings";
import { Aircraft } from "./aircraftClass";
import { DisplayRange } from "@/context/displayRangeContext";
import type { Coordinate } from "@/context/centerCoordinateContext";
import type { DataBlockDisplaySetting } from "@/context/dataBlockDisplaySettingContext";
import {
  formatAtcClearanceMemoLine,
  isAltitudeClearanceRecorded,
} from "./atcClearanceMemoLine";
import { formatEtaToUtcHhMm } from "./formatEtaUtc";
import { formatAltitudeTargetVsActualLabel } from "./altitudeDataBlockLabel";
import {
  isTrackHistoryFreshForDraw,
  selectTrackDisplaySamples,
  TRACK_DISPLAY_DOT_RADIUS_PX,
} from "./trackHistoryDisplaySamples";
import {
  isEmergencySquawk,
  EMERGENCY_SQUAWKS,
  type EmergencySquawkCode,
} from "./emergencySquawk";
import { MSAW_ALERTS, type MsawAlertDto } from "./msawAlert";
import {
  findThresholdDistance,
  formatThresholdReadout,
  timeToDistanceSeconds,
  type RunwayApproachGeometry,
} from "@/utility/measure/rangeBearing";

const DATA_BLOCK_LINE_HEIGHT_PX = 15;
const DATA_BLOCK_HIT_TOP_PAD_PX = 20;
const DATA_BLOCK_HIT_WIDTH_PX = 70;

/**
 * Class to draw aircraft on the canvas
 * @example
 * import { DrawAircraft } from "./drawAircraft";
 * DrawAircraft.drawAircraft(ctx, aircraft);
 * @see Aircraft
 * @see CoordinateManager
 * @see GLOBAL_SETTINGS
 * @see GLOBAL_CONSTANTS
 * @see CanvasRenderingContext2D
 */
class DrawAircraft {
  /** Same integer as the data-block `R` row; drives STCA ring thresholds so R0 never gets a ring from float/NaN quirks. */
  private static computeRiskDisplayFloor(aircraft: Aircraft): number {
    const raw = aircraft.riskLevel;
    const n =
      typeof raw === "number" && Number.isFinite(raw) ? raw : Number(raw);
    if (!Number.isFinite(n)) {
      return 0;
    }
    return Math.floor(Math.max(0, Math.min(100, n)));
  }

  public static drawAircraft(
    ctx: CanvasRenderingContext2D,
    aircraft: Aircraft,
    displayRange: DisplayRange,
    centerCoordinate: Coordinate,
    dataBlockDisplaySetting: DataBlockDisplaySetting,
    /** Controller 画面では管制クリアランス高度があれば 2 行目をクリアランス vs 実測にする（管制メモとパイロット目標を混同しない）。 */
    useControllerClearanceAltitudeRow = false,
    durationMinutes = 1,
    nowMs = performance.now(),
    squawkHighlight: string | null = null,
    simulationSpeedMultiplier = 1,
    runways: readonly RunwayApproachGeometry[] = []
  ) {
    const riskDisplayFloor = this.computeRiskDisplayFloor(aircraft);
    ctx.save();
    try {
      this.drawHoldingPattern(ctx, aircraft, centerCoordinate, displayRange);
      this.drawTrack(
        ctx,
        aircraft,
        centerCoordinate,
        displayRange,
        simulationSpeedMultiplier
      );
      const isSquawkMatched =
        squawkHighlight != null &&
        aircraft.squawk != null &&
        aircraft.squawk === squawkHighlight;
      const emergencySquawk: EmergencySquawkCode | null =
        this.getEmergencySquawk(aircraft.squawk);
      const msawAlert = aircraft.msawAlert;
      this.drawAircraftMarker(
        ctx,
        aircraft.position,
        riskDisplayFloor,
        nowMs,
        isSquawkMatched,
        emergencySquawk,
        msawAlert
      );
      this.drawHeadingLine(
        ctx,
        aircraft.position,
        aircraft.vector.groundSpeed,
        aircraft.vector.heading,
        displayRange,
        durationMinutes
      );
      this.drawLabelLiine(ctx, aircraft.position, aircraft.label);
      this.drawAircraftLabel(
        ctx,
        aircraft,
        dataBlockDisplaySetting,
        useControllerClearanceAltitudeRow,
        riskDisplayFloor,
        isSquawkMatched,
        emergencySquawk,
        msawAlert,
        runways
      );
    } finally {
      ctx.restore();
    }
  }

  /** Hit-test extent below the first label baseline; grows with optional rows + threshold readout. */
  public static labelHitBottomOffsetPx(
    aircraft: Aircraft,
    setting: DataBlockDisplaySetting,
    useControllerClearanceAltitudeRow: boolean,
    runways: readonly RunwayApproachGeometry[]
  ): number {
    return (
      this.countDataBlockLines(
        aircraft,
        setting,
        useControllerClearanceAltitudeRow,
        runways
      ) *
        DATA_BLOCK_LINE_HEIGHT_PX +
      5
    );
  }

  public static labelHitTopPadPx(): number {
    return DATA_BLOCK_HIT_TOP_PAD_PX;
  }

  public static labelHitWidthPx(): number {
    return DATA_BLOCK_HIT_WIDTH_PX;
  }

  /**
   * Instructed hold racetrack from Backend `holding.pattern`.
   * Amber dashed — distinct from cyan RBL and range rings; drawn under the track.
   */
  private static drawHoldingPattern(
    ctx: CanvasRenderingContext2D,
    aircraft: Aircraft,
    centerCoordinate: Coordinate,
    displayRange: DisplayRange
  ): void {
    const holding = aircraft.holding;
    if (holding == null || holding.pattern.length < 2) {
      return;
    }
    ctx.save();
    ctx.strokeStyle = "#fbbf24";
    ctx.lineWidth = 1.25;
    ctx.setLineDash([6, 4]);
    ctx.beginPath();
    holding.pattern.forEach((point, index) => {
      const canvas = CoordinateManager.calculateCanvasCoordinates(
        { latitude: point.latitude, longitude: point.longitude },
        centerCoordinate,
        displayRange
      );
      if (index === 0) {
        ctx.moveTo(canvas.x, canvas.y);
      } else {
        ctx.lineTo(canvas.x, canvas.y);
      }
    });
    ctx.stroke();
    ctx.restore();
  }

  public static thresholdReadoutLine(
    aircraft: Aircraft,
    runways: readonly RunwayApproachGeometry[]
  ): string | null {
    if (runways.length === 0) {
      return null;
    }
    const result = findThresholdDistance(aircraft.geoPosition, runways);
    if (result == null) {
      return null;
    }
    const timeSec = timeToDistanceSeconds(
      result.alongTrackNm,
      aircraft.vector.groundSpeed
    );
    return formatThresholdReadout(
      result.designator,
      result.alongTrackNm,
      timeSec
    );
  }

  private static countDataBlockLines(
    aircraft: Aircraft,
    setting: DataBlockDisplaySetting,
    useControllerClearanceAltitudeRow: boolean,
    runways: readonly RunwayApproachGeometry[]
  ): number {
    let lines = 3;
    if (this.thresholdReadoutLine(aircraft, runways) != null) {
      lines += 1;
    }
    const clearance = aircraft.atcClearance;
    const useClearanceAltitudePrimaryRow =
      useControllerClearanceAltitudeRow &&
      clearance != null &&
      isAltitudeClearanceRecorded(clearance);
    if (setting.atcClearanceMemo) {
      const memoLine = formatAtcClearanceMemoLine(
        aircraft.atcClearance,
        {
          altitudeFt: aircraft.position.altitude,
          heading: aircraft.vector.heading,
          groundSpeed: aircraft.vector.groundSpeed,
        },
        { omitAltitude: useClearanceAltitudePrimaryRow }
      );
      if (memoLine) {
        lines += 1;
      }
    }
    lines += 1;
    if (aircraft.navigationMode === "HOLDING") {
      lines += 1;
    }
    if (aircraft.goAround) {
      lines += 1;
    }
    if (aircraft.towerHandoff) {
      lines += 1;
    }
    if (setting.aircraftType && aircraft.model) {
      lines += 1;
    }
    if (setting.eta && aircraft.eta && formatEtaToUtcHhMm(aircraft.eta)) {
      lines += 1;
    }
    const emergencySquawk = this.getEmergencySquawk(aircraft.squawk);
    if (emergencySquawk !== null) {
      lines += 1;
    }
    if (aircraft.msawAlert !== null) {
      lines += 1;
    }
    if (setting.squawk && !emergencySquawk) {
      lines += 1;
    }
    return lines;
  }

  private static drawTrack(
    ctx: CanvasRenderingContext2D,
    aircraft: Aircraft,
    centerCoordinate: Coordinate,
    displayRange: DisplayRange,
    simulationSpeedMultiplier: number
  ) {
    const nowMs = Date.now();
    if (!isTrackHistoryFreshForDraw(aircraft.positionHistory, nowMs)) {
      return;
    }
    const radius = TRACK_DISPLAY_DOT_RADIUS_PX;
    const samples = selectTrackDisplaySamples(
      aircraft.positionHistory,
      nowMs,
      simulationSpeedMultiplier
    );
    for (const sample of samples) {
      const p = CoordinateManager.calculateCanvasCoordinates(
        { latitude: sample.latitude, longitude: sample.longitude },
        centerCoordinate,
        displayRange
      );
      ctx.beginPath();
      ctx.arc(p.x, p.y, radius, 0, 2 * Math.PI);
      ctx.fillStyle = "white";
      ctx.fill();
    }
  }

  private static drawAircraftMarker(
    ctx: CanvasRenderingContext2D,
    position: { x: number; y: number },
    riskDisplayFloor: number,
    nowMs: number,
    isSquawkMatched: boolean,
    emergencySquawk: EmergencySquawkCode | null,
    msawAlert: MsawAlertDto | null
  ) {
    const radius: number = 5;
    const flashOn = Math.floor(nowMs / 450) % 2 === 0;

    if (riskDisplayFloor >= 70) {
      if (flashOn) {
        ctx.beginPath();
        ctx.arc(position.x, position.y, radius + 6, 0, 2 * Math.PI);
        ctx.strokeStyle = "rgba(207, 34, 34, 0.9)";
        ctx.lineWidth = 3;
        ctx.stroke();
      }
    } else if (emergencySquawk !== null) {
      ctx.beginPath();
      ctx.arc(position.x, position.y, radius + 5, 0, 2 * Math.PI);
      const color = EMERGENCY_SQUAWKS[emergencySquawk].color;
      ctx.strokeStyle = `${color}f2`;
      ctx.lineWidth = 3;
      ctx.stroke();
    } else if (msawAlert !== null) {
      ctx.beginPath();
      ctx.arc(position.x, position.y, radius + 5, 0, 2 * Math.PI);
      const color = MSAW_ALERTS[msawAlert.type].color;
      ctx.strokeStyle = `${color}f2`;
      ctx.lineWidth = 2;
      ctx.stroke();
    } else if (riskDisplayFloor >= 30) {
      ctx.beginPath();
      ctx.arc(position.x, position.y, radius + 4, 0, 2 * Math.PI);
      ctx.strokeStyle = "rgba(158, 106, 3, 0.95)";
      ctx.lineWidth = 2;
      ctx.stroke();
    } else if (isSquawkMatched) {
      ctx.beginPath();
      ctx.arc(position.x, position.y, radius + 4, 0, 2 * Math.PI);
      ctx.strokeStyle = "rgba(56, 189, 248, 0.95)";
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    ctx.beginPath();
    ctx.arc(position.x, position.y, radius, 0, 2 * Math.PI);
    ctx.fillStyle = "white";
    ctx.fill();
  }

  private static drawHeadingLine(
    ctx: CanvasRenderingContext2D,
    position: { x: number; y: number },
    groundSpeed: number,
    heading: number,
    displayRange: DisplayRange,
    durationMinutes: number
  ) {
    const futurePosition = CoordinateManager.calculateFuturePositionOnCanvas(
      groundSpeed,
      heading,
      GLOBAL_SETTINGS.canvasWidth,
      GLOBAL_SETTINGS.canvasHeight,
      displayRange,
      position,
      durationMinutes
    );

    ctx.beginPath();
    ctx.moveTo(position.x, position.y);
    ctx.lineTo(futurePosition.futureX, futurePosition.futureY);
    ctx.strokeStyle = "white";
    ctx.lineWidth = 1;
    ctx.stroke();
  }

  private static getEmergencySquawk(
    squawk: string | null
  ): EmergencySquawkCode | null {
    return isEmergencySquawk(squawk) ? squawk : null;
  }

  private static drawAircraftLabel(
    ctx: CanvasRenderingContext2D,
    aircraft: Aircraft,
    setting: DataBlockDisplaySetting,
    useControllerClearanceAltitudeRow: boolean,
    riskDisplayFloor: number,
    isSquawkMatched: boolean,
    emergencySquawk: EmergencySquawkCode | null,
    msawAlert: MsawAlertDto | null,
    runways: readonly RunwayApproachGeometry[]
  ) {
    const airplanePosition = aircraft.position;
    const instructedVector = aircraft.instructedVector;
    const labelX: number = airplanePosition.x + aircraft.label.x;
    let lineY: number = airplanePosition.y - aircraft.label.y;

    const clearance = aircraft.atcClearance;
    const useClearanceAltitudePrimaryRow =
      useControllerClearanceAltitudeRow &&
      clearance != null &&
      isAltitudeClearanceRecorded(clearance);

    const altitudeLabel =
      useClearanceAltitudePrimaryRow && clearance
        ? formatAltitudeTargetVsActualLabel(
            clearance.altitude,
            airplanePosition.altitude
          )
        : formatAltitudeTargetVsActualLabel(
            instructedVector.altitude,
            airplanePosition.altitude
          );

    let riskColor = "white";
    if (riskDisplayFloor >= 70) {
      riskColor = "red";
    } else if (riskDisplayFloor >= 30) {
      riskColor = "yellow";
    }

    ctx.fillStyle = "white";
    ctx.font = GLOBAL_CONSTANTS.FONT_STYLE_IN_CANVAS;
    ctx.textAlign = "left";

    const lineHeight = 15;

    if (isSquawkMatched && riskDisplayFloor < 30) {
      ctx.fillStyle = "rgba(56, 189, 248, 0.25)";
      ctx.fillRect(labelX - 2, lineY - 11, 74, 15);
      ctx.fillStyle = "white";
    }
    ctx.fillText(aircraft.callsign, labelX, lineY);
    lineY += lineHeight;

    ctx.fillText(altitudeLabel, labelX, lineY);
    lineY += lineHeight;

    ctx.fillStyle = "white";
    ctx.fillText(
      "G" + Math.floor(aircraft.vector.groundSpeed / 10).toString(),
      labelX,
      lineY
    );
    if (aircraft.indicatedAirspeedKnots != null) {
      ctx.fillText(
        "I" + Math.floor(aircraft.indicatedAirspeedKnots / 10).toString(),
        labelX + 20,
        lineY
      );
    }
    ctx.fillText(
      aircraft.destinationIcao.length >= 4
        ? aircraft.destinationIcao.slice(-3)
        : aircraft.destinationIcao,
      labelX + 40,
      lineY
    );
    lineY += lineHeight;

    const thresholdLine = this.thresholdReadoutLine(aircraft, runways);
    if (thresholdLine != null) {
      ctx.fillStyle = "#a5f3fc";
      ctx.fillText(thresholdLine, labelX, lineY);
      lineY += lineHeight;
      ctx.fillStyle = "white";
    }

    if (setting.atcClearanceMemo) {
      const memoLine = formatAtcClearanceMemoLine(
        aircraft.atcClearance,
        {
          altitudeFt: airplanePosition.altitude,
          heading: aircraft.vector.heading,
          groundSpeed: aircraft.vector.groundSpeed,
        },
        { omitAltitude: useClearanceAltitudePrimaryRow }
      );
      if (memoLine) {
        ctx.fillStyle = "#93c5fd";
        ctx.fillText(memoLine, labelX, lineY);
        lineY += lineHeight;
      }
    }

    ctx.fillStyle = riskColor;
    ctx.fillText("R" + riskDisplayFloor.toString(), labelX, lineY);
    lineY += lineHeight;

    if (aircraft.navigationMode === "HOLDING") {
      ctx.fillStyle = "#f59e0b";
      ctx.fillText("HLD", labelX, lineY);
      lineY += lineHeight;
    }

    if (aircraft.goAround) {
      ctx.fillStyle = "#f43f5e";
      ctx.fillText("GA", labelX, lineY);
      lineY += lineHeight;
    }

    if (aircraft.towerHandoff) {
      ctx.fillStyle = "#38bdf8";
      ctx.fillText("TWR", labelX, lineY);
      lineY += lineHeight;
    }

    if (setting.aircraftType && aircraft.model) {
      ctx.fillStyle = "white";
      const modelDisplay =
        aircraft.model.length > 6 ? aircraft.model.slice(0, 6) : aircraft.model;
      ctx.fillText(modelDisplay, labelX, lineY);
      lineY += lineHeight;
    }

    if (setting.eta && aircraft.eta) {
      const etaFormatted = formatEtaToUtcHhMm(aircraft.eta);
      if (etaFormatted) {
        ctx.fillStyle = "white";
        ctx.fillText(etaFormatted, labelX, lineY);
        lineY += lineHeight;
      }
    }

    if (emergencySquawk !== null) {
      const config = EMERGENCY_SQUAWKS[emergencySquawk];
      ctx.fillStyle = config.color;
      ctx.fillText(config.label, labelX, lineY);
      lineY += lineHeight;
    }

    if (msawAlert !== null) {
      const config = MSAW_ALERTS[msawAlert.type];
      ctx.fillStyle = config.color;
      ctx.fillText(config.label, labelX, lineY);
      lineY += lineHeight;
    }

    if (setting.squawk && !emergencySquawk) {
      ctx.fillStyle = "white";
      const squawkDisplay = aircraft.squawk ?? "---";
      ctx.fillText(squawkDisplay, labelX, lineY);
    }
  }

  private static drawLabelLiine(
    ctx: CanvasRenderingContext2D,
    aircraftPosition: { x: number; y: number },
    labelPosition: { x: number; y: number }
  ) {
    const labelX: number = aircraftPosition.x + labelPosition.x;
    const labelY: number = aircraftPosition.y - labelPosition.y;
    const labelDistance: number = Math.sqrt(
      Math.pow(labelPosition.x, 2) + Math.pow(labelPosition.y, 2)
    );
    const sin = labelPosition.y / labelDistance;
    const cos = labelPosition.x / labelDistance;

    ctx.beginPath();
    ctx.moveTo(aircraftPosition.x + 10 * cos, aircraftPosition.y - 10 * sin);
    ctx.lineTo(labelX - 5, labelY + 15);
    ctx.strokeStyle = "white";
    ctx.lineWidth = 1;
    ctx.stroke();
  }
}

export { DrawAircraft };
