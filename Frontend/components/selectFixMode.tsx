"use client";
import { useSelectFixMode } from "@/context/selectFixModeContext";
import { useSelectedAircraft } from "@/context/selectedAircraftContext";
import {
  defaultHoldLegMinutes,
  directToFix,
  holdAtFix,
  type HoldTurnDirection,
} from "@/utility/api/flightPlan";
import { initialBearingDeg } from "@/utility/measure/rangeBearing";
import React, { useEffect, useState } from "react";

type LegMode = "minutes" | "nm";

const SelectFixMode = () => {
  const {
    isSelectFixMode,
    setIsSelectFixMode,
    selectedFixName,
    setSelectedFixName,
    selectedFixGeo,
    setSelectedFixGeo,
  } = useSelectFixMode();
  const { callsign, instructedVector, selectedGeo } = useSelectedAircraft();
  const [commandType, setCommandType] = useState<"DIRECT_TO" | "HOLD">(
    "DIRECT_TO"
  );
  const [inboundCourseDeg, setInboundCourseDeg] = useState("090");
  const [legMode, setLegMode] = useState<LegMode>("minutes");
  const [legMinutes, setLegMinutes] = useState("1.0");
  const [legNm, setLegNm] = useState("4.0");
  const [turnDirection, setTurnDirection] =
    useState<HoldTurnDirection>("RIGHT");

  useEffect(() => {
    if (commandType !== "HOLD") return;
    if (selectedGeo && selectedFixGeo) {
      const bearing = Math.round(
        initialBearingDeg(
          {
            latitude: selectedGeo.latitude,
            longitude: selectedGeo.longitude,
          },
          selectedFixGeo
        )
      );
      setInboundCourseDeg(String(bearing).padStart(3, "0"));
    }
    const altitudeFt = selectedGeo?.altitude ?? instructedVector.altitude;
    setLegMinutes(String(defaultHoldLegMinutes(altitudeFt)));
    setLegMode("minutes");
    setTurnDirection("RIGHT");
  }, [
    commandType,
    selectedFixName,
    selectedFixGeo,
    selectedGeo,
    instructedVector.altitude,
  ]);

  const resetSelection = () => {
    setSelectedFixName("No fixes selected");
    setSelectedFixGeo(null);
    setCommandType("DIRECT_TO");
    setIsSelectFixMode({ selectFixMode: false });
  };

  return (
    <div className="bg-atc-surface border border-atc-border rounded-lg p-4 mt-4">
      <div className="text-center mb-4">
        <p className="text-lg font-bold text-atc-text font-mono tracking-wider">
          {selectedFixName}
        </p>
        <div className="mt-1 h-px bg-atc-border" />
      </div>

      {!isSelectFixMode.selectFixMode ? (
        <button
          className="w-full px-4 py-3 bg-atc-surface-elevated text-atc-text font-bold text-sm
                     rounded-lg border border-atc-accent
                     transition-colors duration-200
                     hover:bg-atc-accent hover:text-white
                     focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg"
          onClick={() => {
            if (!callsign || callsign.length < 2) {
              setSelectedFixName("Select aircraft first");
              return;
            }
            setSelectedFixName("No fixes selected");
            setSelectedFixGeo(null);
            setCommandType("DIRECT_TO");
            setIsSelectFixMode({ selectFixMode: true });
          }}
        >
          <span className="font-mono tracking-wider">DIRECT TO FIX</span>
        </button>
      ) : (
        <div className="flex flex-col space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <button
              className={`px-3 py-2 text-xs font-bold rounded border transition-colors ${
                commandType === "DIRECT_TO"
                  ? "bg-atc-accent text-white border-atc-accent"
                  : "bg-atc-surface-elevated text-atc-text border-atc-border"
              }`}
              onClick={() => setCommandType("DIRECT_TO")}
              type="button"
            >
              DIRECT TO
            </button>
            <button
              className={`px-3 py-2 text-xs font-bold rounded border transition-colors ${
                commandType === "HOLD"
                  ? "bg-atc-warning text-white border-atc-warning"
                  : "bg-atc-surface-elevated text-atc-text border-atc-border"
              }`}
              onClick={() => setCommandType("HOLD")}
              type="button"
            >
              HOLD
            </button>
          </div>

          {commandType === "HOLD" && (
            <div className="space-y-2 border border-atc-border rounded p-2">
              <label className="block text-xs text-atc-text-muted">
                Inbound course (°)
                <input
                  className="mt-1 w-full px-2 py-1 text-xs font-mono bg-atc-surface-elevated border border-atc-border rounded"
                  value={inboundCourseDeg}
                  onChange={(e) => setInboundCourseDeg(e.target.value)}
                  inputMode="decimal"
                  aria-label="Inbound course degrees"
                />
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  className={`px-2 py-1 text-xs font-bold rounded border ${
                    legMode === "minutes"
                      ? "bg-atc-accent text-white border-atc-accent"
                      : "bg-atc-surface-elevated text-atc-text border-atc-border"
                  }`}
                  onClick={() => setLegMode("minutes")}
                >
                  MINUTES
                </button>
                <button
                  type="button"
                  className={`px-2 py-1 text-xs font-bold rounded border ${
                    legMode === "nm"
                      ? "bg-atc-accent text-white border-atc-accent"
                      : "bg-atc-surface-elevated text-atc-text border-atc-border"
                  }`}
                  onClick={() => setLegMode("nm")}
                >
                  NM
                </button>
              </div>
              {legMode === "minutes" ? (
                <label className="block text-xs text-atc-text-muted">
                  Leg (min)
                  <input
                    className="mt-1 w-full px-2 py-1 text-xs font-mono bg-atc-surface-elevated border border-atc-border rounded"
                    value={legMinutes}
                    onChange={(e) => setLegMinutes(e.target.value)}
                    inputMode="decimal"
                    aria-label="Leg minutes"
                  />
                </label>
              ) : (
                <label className="block text-xs text-atc-text-muted">
                  Leg (NM)
                  <input
                    className="mt-1 w-full px-2 py-1 text-xs font-mono bg-atc-surface-elevated border border-atc-border rounded"
                    value={legNm}
                    onChange={(e) => setLegNm(e.target.value)}
                    inputMode="decimal"
                    aria-label="Leg nautical miles"
                  />
                </label>
              )}
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  className={`px-2 py-1 text-xs font-bold rounded border ${
                    turnDirection === "RIGHT"
                      ? "bg-atc-accent text-white border-atc-accent"
                      : "bg-atc-surface-elevated text-atc-text border-atc-border"
                  }`}
                  onClick={() => setTurnDirection("RIGHT")}
                >
                  RIGHT
                </button>
                <button
                  type="button"
                  className={`px-2 py-1 text-xs font-bold rounded border ${
                    turnDirection === "LEFT"
                      ? "bg-atc-warning text-white border-atc-warning"
                      : "bg-atc-surface-elevated text-atc-text border-atc-border"
                  }`}
                  onClick={() => setTurnDirection("LEFT")}
                >
                  LEFT
                </button>
              </div>
            </div>
          )}

          <button
            className="w-full px-4 py-3 bg-atc-accent text-white font-bold text-sm
                       rounded-lg border border-transparent
                       transition-colors duration-200
                       hover:bg-atc-accent-hover
                       focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg"
            onClick={() => {
              void (async () => {
                if (!callsign || callsign.length < 2) return;
                if (
                  !selectedFixName ||
                  selectedFixName === "No fixes selected"
                ) {
                  return;
                }
                try {
                  let ok = false;
                  if (commandType === "DIRECT_TO") {
                    ok = await directToFix(callsign, selectedFixName, false);
                  } else {
                    const course = Number(inboundCourseDeg);
                    const leg =
                      legMode === "minutes"
                        ? {
                            kind: "minutes" as const,
                            legMinutes: Number(legMinutes),
                          }
                        : { kind: "nm" as const, legNm: Number(legNm) };
                    ok = await holdAtFix(callsign, {
                      fixName: selectedFixName,
                      turnDirection,
                      inboundCourseDeg: course,
                      leg,
                    });
                  }
                  if (ok) {
                    console.log(
                      `Aircraft ${callsign} controlled successfully.`
                    );
                  } else {
                    console.error(
                      `Failed to control aircraft ${callsign}. Status:`
                    );
                  }
                } catch (error) {
                  console.error(
                    "Error occurred while controlling aircraft:",
                    error
                  );
                }

                resetSelection();
              })();
            }}
          >
            <span className="font-mono tracking-wider">✓ CONFIRM</span>
          </button>

          <button
            className="w-full px-4 py-3 bg-atc-danger text-white font-bold text-sm
                       rounded-lg border border-transparent
                       transition-opacity duration-200
                       hover:opacity-90
                       focus:outline-none focus:ring-2 focus:ring-atc-danger focus:ring-offset-2 focus:ring-offset-atc-bg"
            onClick={() => {
              resetSelection();
            }}
          >
            <span className="font-mono tracking-wider">✕ CANCEL</span>
          </button>
        </div>
      )}
    </div>
  );
};

export default SelectFixMode;
