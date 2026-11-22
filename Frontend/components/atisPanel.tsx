"use client";

import React, { useEffect, useState } from "react";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";
import {
  fetchAtisAll,
  updateAtis,
  WIND_DIRECTION_DEG_LIMITS,
  WIND_SPEED_KT_LIMITS,
  type AtisState,
  type AtisWindUpdate,
} from "@/utility/api/atis";

type AtisPanelProps = {
  editable: boolean;
};

const DEFAULT_AIRPORT = "RJTT";

function windInputsFromState(state: AtisState): {
  direction: string;
  speed: string;
} {
  if (!state.windAvailable) {
    return { direction: "", speed: "" };
  }
  if (state.windSpeedKt === 0) {
    return { direction: "", speed: "0" };
  }
  return {
    direction:
      state.windDirectionDeg != null ? String(state.windDirectionDeg) : "",
    speed: state.windSpeedKt != null ? String(state.windSpeedKt) : "",
  };
}

function parseWindFromInputs(
  directionText: string,
  speedText: string
): { ok: true; wind: AtisWindUpdate } | { ok: false; message: string } {
  const speed = Number.parseInt(speedText.trim(), 10);
  if (
    !Number.isInteger(speed) ||
    speed < WIND_SPEED_KT_LIMITS.min ||
    speed > WIND_SPEED_KT_LIMITS.max
  ) {
    return {
      ok: false,
      message: `Wind speed must be ${WIND_SPEED_KT_LIMITS.min}–${WIND_SPEED_KT_LIMITS.max} kt`,
    };
  }
  if (speed === 0) {
    return { ok: true, wind: { speedKt: 0 } };
  }
  const direction = Number.parseInt(directionText.trim(), 10);
  if (
    !Number.isInteger(direction) ||
    direction < WIND_DIRECTION_DEG_LIMITS.min ||
    direction > WIND_DIRECTION_DEG_LIMITS.max
  ) {
    return {
      ok: false,
      message: `Wind direction must be ${WIND_DIRECTION_DEG_LIMITS.min}–${WIND_DIRECTION_DEG_LIMITS.max} deg`,
    };
  }
  return { ok: true, wind: { directionDeg: direction, speedKt: speed } };
}

const AtisPanel: React.FC<AtisPanelProps> = ({ editable }) => {
  const [states, setStates] = useState<AtisState[]>([]);
  const [selectedIcao, setSelectedIcao] = useState(DEFAULT_AIRPORT);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [windDirection, setWindDirection] = useState("");
  const [windSpeed, setWindSpeed] = useState("");

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      const next = await fetchAtisAll();
      if (!cancelled && next != null) {
        setStates(next);
      }
    };
    void tick();
    const id = setInterval(
      () => void tick(),
      GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL
    );
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, []);

  const current =
    states.find((s) => s.airportIcao === selectedIcao) ?? states[0] ?? null;

  const windStateKey = current
    ? `${current.airportIcao}|${current.windAvailable}|${current.windDirectionDeg ?? ""}|${current.windSpeedKt ?? ""}`
    : "";

  useEffect(() => {
    if (current == null) {
      return;
    }
    const inputs = windInputsFromState(current);
    setWindDirection(inputs.direction);
    setWindSpeed(inputs.speed);
  }, [current, windStateKey]);

  const applyAtisUpdate = async (request: Parameters<typeof updateAtis>[1]) => {
    if (!editable || current == null) {
      return;
    }
    setSaving(true);
    setError("");
    const result = await updateAtis(current.airportIcao, request);
    setSaving(false);
    if (!result.ok) {
      setError(result.message);
      return;
    }
    setStates((prev) =>
      prev.map((s) =>
        s.airportIcao === result.state.airportIcao ? result.state : s
      )
    );
  };

  const onRunwayChange = async (runwayInUse: string) => {
    if (!editable || current == null || runwayInUse === current.runwayInUse) {
      return;
    }
    await applyAtisUpdate({ runwayInUse });
  };

  const onApplyWind = async () => {
    const parsed = parseWindFromInputs(windDirection, windSpeed);
    if (!parsed.ok) {
      setError(parsed.message);
      return;
    }
    if (current == null) {
      return;
    }
    await applyAtisUpdate({
      runwayInUse: current.runwayInUse,
      wind: parsed.wind,
    });
  };

  const onSetCalm = async () => {
    if (current == null) {
      return;
    }
    await applyAtisUpdate({
      runwayInUse: current.runwayInUse,
      wind: { speedKt: 0 },
    });
  };

  const onClearWind = async () => {
    if (current == null) {
      return;
    }
    await applyAtisUpdate({
      runwayInUse: current.runwayInUse,
      wind: null,
    });
  };

  if (current == null) {
    return null;
  }

  return (
    <div
      className="absolute right-3 top-3 z-10 w-[min(100%-1.5rem,22rem)] rounded border border-atc-border bg-atc-surface/90 px-3 py-2 text-xs text-atc-text shadow-sm backdrop-blur-sm"
      aria-live="polite"
    >
      <div className="mb-1 flex items-center justify-between gap-2">
        <span className="font-semibold tracking-wide text-atc-text-muted">
          ATIS / RWY IN USE
        </span>
        {states.length > 1 && (
          <select
            className="rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
            value={current.airportIcao}
            onChange={(e) => setSelectedIcao(e.target.value)}
            aria-label="ATIS airport"
          >
            {states.map((s) => (
              <option key={s.airportIcao} value={s.airportIcao}>
                {s.airportIcao}
              </option>
            ))}
          </select>
        )}
      </div>
      <div className="mb-1 font-mono text-[11px] text-atc-text">
        {current.airportIcao} · INFO {current.informationIdent} · RWY{" "}
        {editable ? (
          <select
            className="ml-1 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono"
            value={current.runwayInUse}
            disabled={saving}
            onChange={(e) => void onRunwayChange(e.target.value)}
            aria-label="Runway in use"
          >
            {current.availableRunways.map((rwy) => (
              <option key={rwy} value={rwy}>
                {rwy}
              </option>
            ))}
          </select>
        ) : (
          <span className="font-semibold">{current.runwayInUse}</span>
        )}
      </div>
      {editable && (
        <div className="mb-1 flex flex-wrap items-end gap-1 border-t border-atc-border/60 pt-1">
          <label className="flex flex-col gap-0.5 font-mono text-[10px] text-atc-text-muted">
            DIR
            <input
              type="number"
              min={WIND_DIRECTION_DEG_LIMITS.min}
              max={WIND_DIRECTION_DEG_LIMITS.max}
              className="w-14 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
              value={windDirection}
              disabled={saving}
              onChange={(e) => setWindDirection(e.target.value)}
              aria-label="Wind direction degrees"
              placeholder="—"
            />
          </label>
          <label className="flex flex-col gap-0.5 font-mono text-[10px] text-atc-text-muted">
            KT
            <input
              type="number"
              min={WIND_SPEED_KT_LIMITS.min}
              max={WIND_SPEED_KT_LIMITS.max}
              className="w-12 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
              value={windSpeed}
              disabled={saving}
              onChange={(e) => setWindSpeed(e.target.value)}
              aria-label="Wind speed knots"
              placeholder="—"
            />
          </label>
          <button
            type="button"
            className="rounded border border-atc-accent px-1.5 py-0.5 font-mono text-[10px] text-atc-text hover:bg-atc-accent hover:text-white disabled:opacity-50"
            disabled={saving}
            onClick={() => void onApplyWind()}
          >
            Apply wind
          </button>
          <button
            type="button"
            className="rounded border border-atc-border px-1.5 py-0.5 font-mono text-[10px] text-atc-text hover:bg-atc-surface-elevated disabled:opacity-50"
            disabled={saving}
            onClick={() => void onSetCalm()}
          >
            CALM
          </button>
          <button
            type="button"
            className="rounded border border-atc-border px-1.5 py-0.5 font-mono text-[10px] text-atc-text-muted hover:bg-atc-surface-elevated disabled:opacity-50"
            disabled={saving}
            onClick={() => void onClearWind()}
          >
            Clear
          </button>
        </div>
      )}
      <pre className="whitespace-pre-wrap font-mono text-[10px] leading-snug text-atc-text-muted">
        {current.text}
      </pre>
      {error !== "" && <p className="mt-1 text-atc-danger">{error}</p>}
    </div>
  );
};

export default AtisPanel;
