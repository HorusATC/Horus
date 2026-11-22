"use client";
import React, { useEffect, useMemo, useRef, useState } from "react";
import { Aircraft } from "@/utility/aircraft/aircraftClass";
import loadAtsRoutes from "@/utility/AtsRouteManager/atsRoutesLoader";
import {
  renderMap,
  drawRangeRings,
  drawRunways,
  type RunwayMapGeometry,
} from "@/utility/AtsRouteManager/routeRenderer";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";
import { GLOBAL_SETTINGS } from "@/utility/globals/settings";
import { fetchAircraftLocation } from "@/utility/api/location";
import {
  beginLocationPoll,
  createLocationPollGate,
  finishLocationPoll,
} from "@/utility/api/locationPollGate";
import { fetchSimulationSpeed } from "@/utility/api/simulation";
import {
  DATA_BLOCK_HIT_LEFT_PAD_PX,
  DrawAircraft,
} from "@/utility/aircraft/drawAircraft";
import {
  computeLabelPlacements,
  type LabelPlacementAircraft,
} from "@/utility/aircraft/labelPlacement";
import { useRouteInfoDisplaySetting } from "@/context/routeInfoDisplaySettingContext";
import { useCenterCoordinate } from "@/context/centerCoordinateContext";
import { useDisplayRange } from "@/context/displayRangeContext";
import { useVelocityVectorLookahead } from "@/context/velocityVectorLookaheadContext";
import { useRangeRingsSetting } from "@/context/rangeRingsSettingContext";
import {
  useDataBlockDisplaySetting,
  type DataBlockDisplaySetting,
} from "@/context/dataBlockDisplaySettingContext";
import { useSelectFixMode } from "@/context/selectFixModeContext";
import { useSelectedAircraft } from "@/context/selectedAircraftContext";
import { searchFixName } from "@/utility/AtsRouteManager/FixNameSearch";
import { usePathname } from "next/navigation";
import CoordinateManager from "@/utility/coordinateManager/CoordinateManager";
import {
  useRblTool,
  type RblEndpoint,
  type RblLine,
} from "@/context/rblToolContext";
import {
  formatRblLabel,
  haversineDistanceNm,
  horizontalCpaSeconds,
  initialBearingDeg,
} from "@/utility/measure/rangeBearing";

function resetCanvas2dState(ctx: CanvasRenderingContext2D): void {
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = "source-over";
  ctx.lineWidth = 1;
  ctx.lineCap = "butt";
  ctx.lineJoin = "miter";
  ctx.setLineDash([]);
  ctx.shadowBlur = 0;
  ctx.shadowColor = "transparent";
}

const RadarCanvas: React.FC = () => {
  const canvasRefs = [
    useRef<HTMLCanvasElement>(null),
    useRef<HTMLCanvasElement>(null),
  ];
  const [controllingAircrafts, setControllingAircrafts] = useState<Aircraft[]>(
    []
  );
  const controllingAircraftsRef = useRef<Aircraft[]>(controllingAircrafts);
  const [selectedAircraft, setSelectedAircraft] = useState<Aircraft | null>(
    null
  );
  const [squawkFilterInput, setSquawkFilterInput] = useState("");
  const selectedAircraftRef = useRef(selectedAircraft);
  const [atsRouteData, setAtsRouteData] = useState<Awaited<
    ReturnType<typeof loadAtsRoutes>
  > | null>(null);
  const [runwayGeometries, setRunwayGeometries] = useState<RunwayMapGeometry[]>(
    []
  );
  const runwayGeometriesRef = useRef(runwayGeometries);
  const [bg, setBg] = useState(0);
  const bgRef = useRef(0);
  const draggingLabelIndexRef = useRef(-1);
  const offsetXRef = useRef(0);
  const offsetYRef = useRef(0);
  const { isDisplaying } = useRouteInfoDisplaySetting();
  const isDisplayingRef = useRef(isDisplaying);
  const { centerCoordinate } = useCenterCoordinate();
  const centerCoordinateRef = useRef(centerCoordinate);
  const { displayRange } = useDisplayRange();
  const displayRangeRef = useRef(displayRange);
  const { durationMinutes } = useVelocityVectorLookahead();
  const velocityVectorDurationRef = useRef(durationMinutes);
  const { rangeRingsSetting } = useRangeRingsSetting();
  const rangeRingsSettingRef = useRef(rangeRingsSetting);
  const {
    toolEnabled: rblToolEnabled,
    activeRbl,
    beginOrCompleteEndpoint,
    handleEscape: handleRblEscape,
  } = useRblTool();
  const rblToolEnabledRef = useRef(rblToolEnabled);
  const activeRblRef = useRef(activeRbl);
  const beginOrCompleteEndpointRef = useRef(beginOrCompleteEndpoint);
  const handleRblEscapeRef = useRef(handleRblEscape);
  const { dataBlockDisplaySetting } = useDataBlockDisplaySetting();
  const pathname = usePathname();
  const dataBlockRadarSetting = useMemo<DataBlockDisplaySetting>(() => {
    const operator = pathname === "/operator";
    return {
      ...dataBlockDisplaySetting,
      atcClearanceMemo: operator
        ? false
        : dataBlockDisplaySetting.atcClearanceMemo,
    };
  }, [pathname, dataBlockDisplaySetting]);
  const dataBlockDisplaySettingRef = useRef(dataBlockRadarSetting);
  const { isSelectFixMode, setSelectedFixName, setSelectedFixGeo } =
    useSelectFixMode();
  const {
    setCallsign,
    setInstructedVector,
    setSelectedGeo,
    setSpeedAsIndicatedAirspeed,
    registerApplyInstructedVectorHandler,
  } = useSelectedAircraft();
  const isSelectFixModeRef = useRef(isSelectFixMode);
  const atsRouteDataRef = useRef(atsRouteData);
  const controllerClearanceAltitudeRowRef = useRef(false);
  const squawkHighlightRef = useRef<string | null>(null);
  const simulationSpeedMultiplierRef = useRef(1);

  // Refs for rAF / setInterval / event handlers: sync during render instead of mirroring in useEffect.
  isDisplayingRef.current = isDisplaying;
  centerCoordinateRef.current = centerCoordinate;
  displayRangeRef.current = displayRange;
  velocityVectorDurationRef.current = durationMinutes;
  rangeRingsSettingRef.current = rangeRingsSetting;
  rblToolEnabledRef.current = rblToolEnabled;
  activeRblRef.current = activeRbl;
  beginOrCompleteEndpointRef.current = beginOrCompleteEndpoint;
  handleRblEscapeRef.current = handleRblEscape;
  dataBlockDisplaySettingRef.current = dataBlockRadarSetting;
  isSelectFixModeRef.current = isSelectFixMode;
  atsRouteDataRef.current = atsRouteData;
  runwayGeometriesRef.current = runwayGeometries;
  selectedAircraftRef.current = selectedAircraft;
  controllingAircraftsRef.current = controllingAircrafts;
  controllerClearanceAltitudeRowRef.current = pathname === "/controller";
  bgRef.current = bg;
  squawkHighlightRef.current =
    squawkFilterInput.trim().length === 4 ? squawkFilterInput.trim() : null;

  useEffect(() => {
    const canvasContainer = document.getElementsByClassName(
      "radarArea"
    )[0] as HTMLElement;

    // Initialize canvas dimensions
    canvasRefs.forEach((canvasRef) => {
      const canvas = canvasRef.current;
      if (canvas) {
        canvas.width = canvasContainer.clientWidth;
        canvas.height = canvasContainer.clientHeight;
      }
    });

    const primaryCanvas = canvasRefs[0]?.current;
    if (primaryCanvas) {
      GLOBAL_SETTINGS.canvasWidth = primaryCanvas.width;
      GLOBAL_SETTINGS.canvasHeight = primaryCanvas.height;
    }

    // Load ATS route data and initialize event listeners
    void initializeAtsRouteData();

    const handleMouseEvents = (canvas: HTMLCanvasElement) => {
      canvas.addEventListener("mousedown", onMouseDown);
      canvas.addEventListener("mousemove", onMouseMove);
      canvas.addEventListener("mouseup", onMouseUp);
      canvas.addEventListener("dblclick", onLabelDoubleClick);
    };

    const removeMouseEvents = (canvas: HTMLCanvasElement) => {
      canvas.removeEventListener("mousedown", onMouseDown);
      canvas.removeEventListener("mousemove", onMouseMove);
      canvas.removeEventListener("mouseup", onMouseUp);
      canvas.removeEventListener("dblclick", onLabelDoubleClick);
    };

    canvasRefs.forEach((canvasRef) => {
      const canvas = canvasRef.current;
      if (canvas) handleMouseEvents(canvas);
    });

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        handleRblEscapeRef.current();
      }
    };
    window.addEventListener("keydown", onKeyDown);

    return () => {
      canvasRefs.forEach((canvasRef) => {
        const canvas = canvasRef.current;
        if (canvas) removeMouseEvents(canvas);
      });
      window.removeEventListener("keydown", onKeyDown);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- setup on mount only; canvasRefs/onMouseDown are refs/stable
  }, []);

  useEffect(() => {
    if (!atsRouteData) return;

    updateCanvas();
    void fetchSimulationSpeed().then((s) => {
      if (s != null) simulationSpeedMultiplierRef.current = s.speedMultiplier;
    });
    const stopUpdating = startUpdatingAircraftLocations();

    let rafId = 0;
    let running = true;
    const renderLoop = () => {
      if (!running) return;
      updateCanvas();
      rafId = requestAnimationFrame(renderLoop);
    };
    rafId = requestAnimationFrame(renderLoop);

    return () => {
      running = false;
      cancelAnimationFrame(rafId);
      stopUpdating();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: run when atsRouteData is set; functions are stable
  }, [atsRouteData]);

  const initializeAtsRouteData = async () => {
    try {
      const data = await loadAtsRoutes();
      setAtsRouteData(data);
    } catch (error) {
      console.error(
        "Error loading ATS routes from initializeAtsRouteData:",
        error
      );
    }
    try {
      const runwaysRes = await fetch("/api/ats/runways");
      if (runwaysRes.ok) {
        const runwaysJson = (await runwaysRes.json()) as RunwayMapGeometry[];
        setRunwayGeometries(Array.isArray(runwaysJson) ? runwaysJson : []);
      }
    } catch (error) {
      console.error("Error loading runways:", error);
    }
  };

  useEffect(() => {
    const unregister = registerApplyInstructedVectorHandler(
      (callsign, instructedVector, indicatedAirspeedKnots) => {
        const aircraft = controllingAircraftsRef.current.find(
          (a) => a.callsign === callsign
        );
        if (aircraft) {
          aircraft.instructedVector = {
            heading: instructedVector.heading,
            groundSpeed: instructedVector.groundSpeed,
            altitude: instructedVector.altitude,
          };
          aircraft.indicatedAirspeedKnots = indicatedAirspeedKnots;
          setControllingAircrafts([...controllingAircraftsRef.current]);
        }
      }
    );
    return unregister;
  }, [registerApplyInstructedVectorHandler]);

  const updateCanvas = () => {
    if (!atsRouteDataRef.current) {
      console.error("ATS Route data is missing or incomplete.");
      return;
    }

    const idx = bgRef.current;
    const ctx = getCanvasContext(idx);
    if (!ctx) return;

    const canvas = canvasRefs[idx]?.current;
    if (!canvas) return;

    const nowMs = performance.now();
    clearCanvas(ctx, canvas);
    resetCanvas2dState(ctx);
    renderMapOnCanvas(ctx);
    renderAircraftsOnCanvas(ctx, nowMs);
    drawRblOnCanvas(ctx);

    toggleCanvasDisplay();
  };

  const getCanvasContext = (index: number): CanvasRenderingContext2D | null => {
    const canvas = canvasRefs[index]?.current;
    if (!canvas) {
      console.error(`Canvas element is not found for index: ${index}`);
      return null;
    }

    return canvas.getContext("2d");
  };

  const renderMapOnCanvas = (ctx: CanvasRenderingContext2D) => {
    if (!atsRouteData) return;
    renderMap(
      atsRouteData.waypoints,
      atsRouteData.radioNavigationAids,
      atsRouteData.atsLowerRoutes,
      atsRouteData.rnavRoutes,
      ctx,
      isDisplayingRef.current,
      centerCoordinateRef.current,
      displayRangeRef.current
    );
    drawRangeRings(ctx, displayRangeRef.current, rangeRingsSettingRef.current);
    drawRunways(
      ctx,
      runwayGeometriesRef.current,
      centerCoordinateRef.current,
      displayRangeRef.current
    );
  };

  const renderAircraftsOnCanvas = (
    ctx: CanvasRenderingContext2D,
    nowMs: number
  ) => {
    controllingAircraftsRef.current.forEach((aircraft) => {
      DrawAircraft.drawAircraft(
        ctx,
        aircraft,
        displayRangeRef.current,
        centerCoordinateRef.current,
        dataBlockDisplaySettingRef.current,
        controllerClearanceAltitudeRowRef.current,
        velocityVectorDurationRef.current,
        nowMs,
        squawkHighlightRef.current,
        simulationSpeedMultiplierRef.current,
        runwayGeometriesRef.current
      );
    });
  };

  const resolveRblEndpointGeo = (
    endpoint: RblEndpoint
  ): {
    latitude: number;
    longitude: number;
    aircraft: Aircraft | null;
  } | null => {
    if (endpoint.kind === "geo") {
      return {
        latitude: endpoint.latitude,
        longitude: endpoint.longitude,
        aircraft: null,
      };
    }
    const aircraft = controllingAircraftsRef.current.find(
      (a) => a.callsign === endpoint.callsign
    );
    if (!aircraft) {
      return null;
    }
    return {
      latitude: aircraft.geoPosition.latitude,
      longitude: aircraft.geoPosition.longitude,
      aircraft,
    };
  };

  const drawRblOnCanvas = (ctx: CanvasRenderingContext2D) => {
    const line: RblLine | null = activeRblRef.current;
    if (line == null) {
      return;
    }
    const start = resolveRblEndpointGeo(line.start);
    const end = resolveRblEndpointGeo(line.end);
    if (start == null || end == null) {
      return;
    }
    const startCanvas = CoordinateManager.calculateCanvasCoordinates(
      { latitude: start.latitude, longitude: start.longitude },
      centerCoordinateRef.current,
      displayRangeRef.current
    );
    const endCanvas = CoordinateManager.calculateCanvasCoordinates(
      { latitude: end.latitude, longitude: end.longitude },
      centerCoordinateRef.current,
      displayRangeRef.current
    );
    ctx.save();
    ctx.strokeStyle = "#67e8f9";
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(startCanvas.x, startCanvas.y);
    ctx.lineTo(endCanvas.x, endCanvas.y);
    ctx.stroke();

    const startGeo = {
      latitude: start.latitude,
      longitude: start.longitude,
    };
    const endGeo = { latitude: end.latitude, longitude: end.longitude };
    const distanceNm = haversineDistanceNm(startGeo, endGeo);
    const bearingDeg = initialBearingDeg(startGeo, endGeo);
    let cpaSeconds: number | null = null;
    if (start.aircraft != null && end.aircraft != null) {
      cpaSeconds = horizontalCpaSeconds(
        startGeo,
        {
          headingDeg:
            start.aircraft.vector.track ?? start.aircraft.vector.heading,
          groundSpeedKt: start.aircraft.vector.groundSpeed,
        },
        endGeo,
        {
          headingDeg: end.aircraft.vector.track ?? end.aircraft.vector.heading,
          groundSpeedKt: end.aircraft.vector.groundSpeed,
        }
      );
    }
    const label = formatRblLabel(distanceNm, bearingDeg, cpaSeconds);
    const midX = (startCanvas.x + endCanvas.x) / 2;
    const midY = (startCanvas.y + endCanvas.y) / 2;
    ctx.fillStyle = "#67e8f9";
    ctx.font = GLOBAL_CONSTANTS.FONT_STYLE_IN_CANVAS;
    ctx.textAlign = "center";
    ctx.fillText(label, midX, midY - 6);
    ctx.restore();
  };

  const clearCanvas = (
    ctx: CanvasRenderingContext2D,
    canvas: HTMLCanvasElement
  ) => {
    ctx.fillStyle = "black";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  };

  const toggleCanvasDisplay = () => {
    const idx = bgRef.current;
    const back = canvasRefs[1 - idx]?.current;
    const front = canvasRefs[idx]?.current;
    if (back) back.style.display = "none";
    if (front) front.style.display = "block";
    const next = 1 - idx;
    bgRef.current = next;
    setBg(next);
  };

  const onMouseDown = (event: MouseEvent) => {
    const canvas = event.target as HTMLCanvasElement;
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const aircraftRadius = 30;
    console.log("Mouse down at", x, y);

    const currentControllingAircrafts = controllingAircraftsRef.current;

    if (isSelectFixModeRef.current.selectFixMode) {
      // フィックス選択モードの場合
      const atsRouteData = atsRouteDataRef.current;
      if (
        !atsRouteData ||
        !atsRouteData.waypoints ||
        !atsRouteData.radioNavigationAids
      ) {
        console.error("atsRouteData is not available");
        return;
      }
      const fixName = searchFixName(
        atsRouteData.waypoints,
        atsRouteData.radioNavigationAids,
        { x, y },
        centerCoordinateRef.current,
        displayRangeRef.current
      );
      if (fixName) {
        setSelectedFixName(fixName);
        const allFixes = [
          ...atsRouteData.waypoints,
          ...atsRouteData.radioNavigationAids,
        ];
        const match = allFixes.find((f) => f.name === fixName);
        setSelectedFixGeo(
          match
            ? { latitude: match.latitude, longitude: match.longitude }
            : null
        );
      }
      return;
    }

    if (rblToolEnabledRef.current) {
      let endpoint: RblEndpoint | null = null;
      for (const aircraft of currentControllingAircrafts) {
        if (isWithinRadius(x, y, aircraft.position, aircraftRadius)) {
          endpoint = { kind: "aircraft", callsign: aircraft.callsign };
          break;
        }
      }
      if (endpoint == null) {
        const geo = CoordinateManager.calculateGeoCoordinates(
          x,
          y,
          centerCoordinateRef.current,
          displayRangeRef.current
        );
        endpoint = {
          kind: "geo",
          latitude: geo.latitude,
          longitude: geo.longitude,
        };
      }
      beginOrCompleteEndpointRef.current(endpoint);
      return;
    }

    for (let index = 0; index < currentControllingAircrafts.length; index++) {
      const aircraft = currentControllingAircrafts[index];
      if (!aircraft) continue;
      const { position, label, callsign } = aircraft;
      const labelX = position.x + label.x;
      const labelY = position.y - label.y;

      if (isWithinRadius(x, y, position, aircraftRadius)) {
        changeDisplayAircraftInfo(aircraft);
        setSelectedAircraft(aircraft);
        console.log("Clicked on aircraft", aircraft);
        break;
      }

      if (isWithinLabelBounds(x, y, labelX, labelY, aircraft)) {
        console.log("Clicked on label", callsign);
        draggingLabelIndexRef.current = index;
        offsetXRef.current = x - labelX;
        offsetYRef.current = y - labelY;
        aircraft.labelPinned = true;
        break;
      }
    }
  };

  const onLabelDoubleClick = (event: MouseEvent) => {
    if (rblToolEnabledRef.current || isSelectFixModeRef.current.selectFixMode) {
      return;
    }
    const canvas = event.target as HTMLCanvasElement;
    const rect = canvas.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const fleet = controllingAircraftsRef.current;
    for (const aircraft of fleet) {
      const labelX = aircraft.position.x + aircraft.label.x;
      const labelY = aircraft.position.y - aircraft.label.y;
      if (!isWithinLabelBounds(x, y, labelX, labelY, aircraft)) {
        continue;
      }
      event.preventDefault();
      aircraft.labelPinned = false;
      applyLabelPlacements(fleet, new Set([aircraft.callsign]));
      setControllingAircrafts([...fleet]);
      break;
    }
  };

  const toPlacementAircraft = (aircraft: Aircraft): LabelPlacementAircraft => ({
    callsign: aircraft.callsign,
    position: aircraft.position,
    label: aircraft.label,
    labelPinned: aircraft.labelPinned,
    hitBottomOffsetPx: DrawAircraft.labelHitBottomOffsetPx(
      aircraft,
      dataBlockDisplaySettingRef.current,
      controllerClearanceAltitudeRowRef.current,
      runwayGeometriesRef.current
    ),
  });

  const applyLabelPlacements = (
    fleet: Aircraft[],
    forcePlaceCallsigns?: ReadonlySet<string>
  ) => {
    computeLabelPlacements(fleet.map(toPlacementAircraft), {
      canvas: {
        width: GLOBAL_SETTINGS.canvasWidth,
        height: GLOBAL_SETTINGS.canvasHeight,
      },
      forcePlaceCallsigns,
    });
  };

  const resetLabelOffsets = () => {
    const fleet = controllingAircraftsRef.current;
    for (const aircraft of fleet) {
      aircraft.labelPinned = false;
    }
    applyLabelPlacements(
      fleet,
      new Set(fleet.map((aircraft) => aircraft.callsign))
    );
    setControllingAircrafts([...fleet]);
  };

  const isWithinRadius = (
    x: number,
    y: number,
    position: { x: number; y: number },
    radius: number
  ) => {
    return (
      x >= position.x - radius &&
      x <= position.x + radius &&
      y >= position.y - radius &&
      y <= position.y + radius
    );
  };

  const isWithinLabelBounds = (
    x: number,
    y: number,
    labelX: number,
    labelY: number,
    aircraft: Aircraft
  ) => {
    const bottom = DrawAircraft.labelHitBottomOffsetPx(
      aircraft,
      dataBlockDisplaySettingRef.current,
      controllerClearanceAltitudeRowRef.current,
      runwayGeometriesRef.current
    );
    return (
      x >= labelX - DATA_BLOCK_HIT_LEFT_PAD_PX &&
      x <= labelX + DrawAircraft.labelHitWidthPx() &&
      y >= labelY - DrawAircraft.labelHitTopPadPx() &&
      y <= labelY + bottom
    );
  };

  const onMouseMove = (event: MouseEvent) => {
    if (draggingLabelIndexRef.current === -1) return;

    const canvas = event.target as HTMLCanvasElement;
    const rect = canvas.getBoundingClientRect();
    const mouseX = event.clientX - rect.left;
    const mouseY = event.clientY - rect.top;

    const aircraft =
      controllingAircraftsRef.current[draggingLabelIndexRef.current];
    if (!aircraft) return;
    const label = aircraft.label;
    const aircraftPosition = aircraft.position;

    label.x = mouseX - offsetXRef.current - aircraftPosition.x;
    label.y = aircraftPosition.y - (mouseY - offsetYRef.current);
    aircraft.labelPinned = true;

    // Update the label position
    setControllingAircrafts([...controllingAircraftsRef.current]);
  };

  const onMouseUp = () => {
    draggingLabelIndexRef.current = -1;
    console.log("Mouse up");
  };

  const changeDisplayAircraftInfo = (aircraft: Aircraft) => {
    setCallsign(aircraft.callsign);
    const iasActive = aircraft.indicatedAirspeedKnots != null;
    setSpeedAsIndicatedAirspeed(iasActive);
    const speedForPanel = iasActive
      ? Math.round(aircraft.indicatedAirspeedKnots!)
      : Math.round(aircraft.instructedVector.groundSpeed);
    setInstructedVector({
      altitude: Math.round(aircraft.instructedVector.altitude),
      groundSpeed: speedForPanel,
      heading: Math.round(aircraft.instructedVector.heading),
    });
    setSelectedGeo({
      latitude: aircraft.geoPosition.latitude,
      longitude: aircraft.geoPosition.longitude,
      altitude: aircraft.position.altitude,
    });
  };

  const startUpdatingAircraftLocations = () => {
    const pollGate = createLocationPollGate();
    const fetchLocationInterval = setInterval(() => {
      void (async () => {
        const seq = beginLocationPoll(pollGate);
        if (seq == null) {
          return;
        }
        try {
          const [speed, locationResult] = await Promise.all([
            fetchSimulationSpeed(),
            fetchAircraftLocation(
              controllingAircraftsRef.current,
              centerCoordinateRef.current,
              displayRangeRef.current
            ),
          ]);
          if (speed != null) {
            simulationSpeedMultiplierRef.current = speed.speedMultiplier;
          }
          const updatedAircrafts = finishLocationPoll(
            pollGate,
            seq,
            locationResult
          );
          if (updatedAircrafts == null) {
            return;
          }
          applyLabelPlacements(updatedAircrafts);
          setControllingAircrafts(() => {
            return updatedAircrafts;
          });
        } catch (error) {
          pollGate.inFlight = false;
          console.error("Error fetching or setting aircraft locations:", error);
        }
      })();
    }, GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL);

    return () => clearInterval(fetchLocationInterval);
  };

  return (
    <div className="radarArea relative h-full w-full">
      <div className="absolute left-3 top-3 z-10 flex items-center gap-2 rounded bg-black/60 p-2 text-xs text-white">
        <span>SQUAWK</span>
        <input
          className="w-20 rounded border border-zinc-500 bg-zinc-900 px-2 py-1 text-white"
          inputMode="numeric"
          maxLength={4}
          placeholder="----"
          value={squawkFilterInput}
          onChange={(e) =>
            setSquawkFilterInput(
              e.target.value.replace(/[^0-9]/g, "").slice(0, 4)
            )
          }
        />
        <button
          type="button"
          className="rounded border border-zinc-500 px-2 py-1"
          onClick={() => setSquawkFilterInput("7600")}
        >
          7600
        </button>
        <button
          type="button"
          className="rounded border border-zinc-500 px-2 py-1"
          onClick={() => setSquawkFilterInput("7700")}
        >
          7700
        </button>
        <button
          type="button"
          className="rounded border border-zinc-500 px-2 py-1"
          onClick={resetLabelOffsets}
          title="Unpin all labels and re-run automatic 8-direction placement"
        >
          Reset label offsets
        </button>
      </div>
      <canvas ref={canvasRefs[0]} className="w-full h-full bg-black"></canvas>
      <canvas ref={canvasRefs[1]} className="w-full hidden"></canvas>
    </div>
  );
};

export default RadarCanvas;
