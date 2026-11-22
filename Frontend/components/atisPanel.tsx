"use client";

import React, { useEffect, useReducer } from "react";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";
import {
  fetchAtisAll,
  updateAtis,
  parseStrictNonNegativeInt,
  QNH_HPA_LIMITS,
  WIND_DIRECTION_DEG_LIMITS,
  WIND_SPEED_KT_LIMITS,
  type AtisState,
  type AtisWindUpdate,
  type UpdateAtisRequest,
} from "@/utility/api/atis";

type AtisPanelProps = {
  editable: boolean;
};

const DEFAULT_AIRPORT = "RJTT";
const CONFLICT_MESSAGE = "ATIS changed by another controller";

export type AtisDraft =
  | { kind: "pristine" }
  | {
      kind: "editing";
      windDirection: string;
      windSpeed: string;
      qnhHpa: string;
    };

type PanelState = {
  servers: AtisState[];
  selectedIcao: string;
  appliedSeq: number;
  drafts: Record<string, AtisDraft>;
  error: string;
  saving: boolean;
};

type PanelAction =
  | { type: "poll_ok"; seq: number; servers: AtisState[] }
  | { type: "select_airport"; icao: string }
  | { type: "set_error"; error: string }
  | { type: "saving"; saving: boolean }
  | {
      type: "edit_field";
      icao: string;
      field: "windDirection" | "windSpeed" | "qnhHpa";
      value: string;
      server: AtisState;
    }
  | {
      type: "apply_ok";
      seq: number;
      state: AtisState;
    }
  | {
      type: "apply_conflict";
      seq: number;
      state: AtisState;
    };

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

function qnhInputFromState(state: AtisState): string {
  if (!state.qnhAvailable || state.qnhHpa == null) {
    return "";
  }
  return String(state.qnhHpa);
}

function draftFor(state: PanelState, icao: string): AtisDraft {
  return state.drafts[icao] ?? { kind: "pristine" };
}

function editingFromServer(
  server: AtisState
): Extract<AtisDraft, { kind: "editing" }> {
  const wind = windInputsFromState(server);
  return {
    kind: "editing",
    windDirection: wind.direction,
    windSpeed: wind.speed,
    qnhHpa: qnhInputFromState(server),
  };
}

function panelReducer(state: PanelState, action: PanelAction): PanelState {
  switch (action.type) {
    case "poll_ok":
      if (action.seq < state.appliedSeq) {
        return state;
      }
      return {
        ...state,
        appliedSeq: action.seq,
        servers: action.servers,
      };
    case "select_airport":
      return { ...state, selectedIcao: action.icao, error: "" };
    case "set_error":
      return { ...state, error: action.error };
    case "saving":
      return { ...state, saving: action.saving };
    case "edit_field": {
      const current = draftFor(state, action.icao);
      const base =
        current.kind === "editing" ? current : editingFromServer(action.server);
      return {
        ...state,
        drafts: {
          ...state.drafts,
          [action.icao]: { ...base, [action.field]: action.value },
        },
      };
    }
    case "apply_ok":
      if (action.seq < state.appliedSeq) {
        return { ...state, saving: false };
      }
      return {
        ...state,
        appliedSeq: action.seq,
        saving: false,
        error: "",
        servers: state.servers.some(
          (s) => s.airportIcao === action.state.airportIcao
        )
          ? state.servers.map((s) =>
              s.airportIcao === action.state.airportIcao ? action.state : s
            )
          : [...state.servers, action.state],
        drafts: {
          ...state.drafts,
          [action.state.airportIcao]: { kind: "pristine" },
        },
      };
    case "apply_conflict":
      if (action.seq < state.appliedSeq) {
        return { ...state, saving: false, error: CONFLICT_MESSAGE };
      }
      return {
        ...state,
        appliedSeq: action.seq,
        saving: false,
        error: CONFLICT_MESSAGE,
        servers: state.servers.some(
          (s) => s.airportIcao === action.state.airportIcao
        )
          ? state.servers.map((s) =>
              s.airportIcao === action.state.airportIcao ? action.state : s
            )
          : [...state.servers, action.state],
      };
  }
}

function parseWindFromInputs(
  directionText: string,
  speedText: string
): { ok: true; wind: AtisWindUpdate } | { ok: false; message: string } {
  const speedParsed = parseStrictNonNegativeInt(
    speedText,
    WIND_SPEED_KT_LIMITS.min,
    WIND_SPEED_KT_LIMITS.max,
    `Wind speed must be ${WIND_SPEED_KT_LIMITS.min}–${WIND_SPEED_KT_LIMITS.max} kt`
  );
  if (!speedParsed.ok) {
    return speedParsed;
  }
  if (speedParsed.value === 0) {
    return { ok: true, wind: { speedKt: 0 } };
  }
  const directionParsed = parseStrictNonNegativeInt(
    directionText,
    WIND_DIRECTION_DEG_LIMITS.min,
    WIND_DIRECTION_DEG_LIMITS.max,
    `Wind direction must be ${WIND_DIRECTION_DEG_LIMITS.min}–${WIND_DIRECTION_DEG_LIMITS.max} deg`
  );
  if (!directionParsed.ok) {
    return directionParsed;
  }
  return {
    ok: true,
    wind: { directionDeg: directionParsed.value, speedKt: speedParsed.value },
  };
}

function parseQnhFromInput(
  qnhText: string
): { ok: true; qnhHpa: number } | { ok: false; message: string } {
  const parsed = parseStrictNonNegativeInt(
    qnhText,
    QNH_HPA_LIMITS.min,
    QNH_HPA_LIMITS.max,
    `QNH must be ${QNH_HPA_LIMITS.min}–${QNH_HPA_LIMITS.max} hPa`
  );
  if (!parsed.ok) {
    return parsed;
  }
  return { ok: true, qnhHpa: parsed.value };
}

const initialPanelState: PanelState = {
  servers: [],
  selectedIcao: DEFAULT_AIRPORT,
  appliedSeq: 0,
  drafts: {},
  error: "",
  saving: false,
};

const AtisPanel: React.FC<AtisPanelProps> = ({ editable }) => {
  const [state, dispatch] = useReducer(panelReducer, initialPanelState);
  const seqRef = React.useRef({ next: 0 });

  const allocateSeq = () => {
    seqRef.current.next += 1;
    return seqRef.current.next;
  };

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      const seq = allocateSeq();
      const next = await fetchAtisAll();
      if (!cancelled && next != null) {
        dispatch({ type: "poll_ok", seq, servers: next });
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
    state.servers.find((s) => s.airportIcao === state.selectedIcao) ??
    state.servers[0] ??
    null;

  const draft =
    current != null
      ? draftFor(state, current.airportIcao)
      : { kind: "pristine" as const };

  const windInputs =
    current == null
      ? { direction: "", speed: "" }
      : draft.kind === "editing"
        ? { direction: draft.windDirection, speed: draft.windSpeed }
        : windInputsFromState(current);

  const qnhInput =
    current == null
      ? ""
      : draft.kind === "editing"
        ? draft.qnhHpa
        : qnhInputFromState(current);

  const applyAtisUpdate = async (
    request: Omit<UpdateAtisRequest, "expectedRevision">
  ) => {
    if (!editable || current == null) {
      return;
    }
    const seq = allocateSeq();
    dispatch({ type: "saving", saving: true });
    dispatch({ type: "set_error", error: "" });
    const result = await updateAtis(current.airportIcao, {
      ...request,
      expectedRevision: current.revision,
    });
    if (result.ok) {
      dispatch({ type: "apply_ok", seq, state: result.state });
      return;
    }
    if (result.conflict) {
      dispatch({ type: "apply_conflict", seq, state: result.state });
      return;
    }
    dispatch({ type: "saving", saving: false });
    dispatch({ type: "set_error", error: result.message });
  };

  const onRunwayChange = async (runwayInUse: string) => {
    if (!editable || current == null || runwayInUse === current.runwayInUse) {
      return;
    }
    await applyAtisUpdate({ runwayInUse });
  };

  const onApplyWind = async () => {
    const parsed = parseWindFromInputs(windInputs.direction, windInputs.speed);
    if (!parsed.ok) {
      dispatch({ type: "set_error", error: parsed.message });
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

  const onApplyQnh = async () => {
    const parsed = parseQnhFromInput(qnhInput);
    if (!parsed.ok) {
      dispatch({ type: "set_error", error: parsed.message });
      return;
    }
    if (current == null) {
      return;
    }
    await applyAtisUpdate({
      runwayInUse: current.runwayInUse,
      qnhHpa: parsed.qnhHpa,
    });
  };

  const onClearQnh = async () => {
    if (current == null) {
      return;
    }
    await applyAtisUpdate({
      runwayInUse: current.runwayInUse,
      qnhHpa: null,
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
        {state.servers.length > 1 && (
          <select
            className="rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
            value={current.airportIcao}
            onChange={(e) =>
              dispatch({ type: "select_airport", icao: e.target.value })
            }
            aria-label="ATIS airport"
          >
            {state.servers.map((s) => (
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
            disabled={state.saving}
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
              type="text"
              inputMode="numeric"
              className="w-14 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
              value={windInputs.direction}
              disabled={state.saving}
              onChange={(e) =>
                dispatch({
                  type: "edit_field",
                  icao: current.airportIcao,
                  field: "windDirection",
                  value: e.target.value,
                  server: current,
                })
              }
              aria-label="Wind direction degrees"
              placeholder="—"
            />
          </label>
          <label className="flex flex-col gap-0.5 font-mono text-[10px] text-atc-text-muted">
            KT
            <input
              type="text"
              inputMode="numeric"
              className="w-12 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
              value={windInputs.speed}
              disabled={state.saving}
              onChange={(e) =>
                dispatch({
                  type: "edit_field",
                  icao: current.airportIcao,
                  field: "windSpeed",
                  value: e.target.value,
                  server: current,
                })
              }
              aria-label="Wind speed knots"
              placeholder="—"
            />
          </label>
          <button
            type="button"
            className="rounded border border-atc-accent px-1.5 py-0.5 font-mono text-[10px] text-atc-text hover:bg-atc-accent hover:text-white disabled:opacity-50"
            disabled={state.saving}
            onClick={() => void onApplyWind()}
          >
            Apply wind
          </button>
          <button
            type="button"
            className="rounded border border-atc-border px-1.5 py-0.5 font-mono text-[10px] text-atc-text hover:bg-atc-surface-elevated disabled:opacity-50"
            disabled={state.saving}
            onClick={() => void onSetCalm()}
          >
            CALM
          </button>
          <button
            type="button"
            className="rounded border border-atc-border px-1.5 py-0.5 font-mono text-[10px] text-atc-text-muted hover:bg-atc-surface-elevated disabled:opacity-50"
            disabled={state.saving}
            onClick={() => void onClearWind()}
          >
            Clear
          </button>
        </div>
      )}
      {editable && (
        <div className="mb-1 flex flex-wrap items-end gap-1 border-t border-atc-border/60 pt-1">
          <label className="flex flex-col gap-0.5 font-mono text-[10px] text-atc-text-muted">
            QNH
            <input
              type="text"
              inputMode="numeric"
              className="w-16 rounded border border-atc-border bg-atc-surface-elevated px-1 py-0.5 font-mono text-[11px]"
              value={qnhInput}
              disabled={state.saving}
              onChange={(e) =>
                dispatch({
                  type: "edit_field",
                  icao: current.airportIcao,
                  field: "qnhHpa",
                  value: e.target.value,
                  server: current,
                })
              }
              aria-label="QNH hectopascals"
              placeholder="—"
            />
          </label>
          <button
            type="button"
            className="rounded border border-atc-accent px-1.5 py-0.5 font-mono text-[10px] text-atc-text hover:bg-atc-accent hover:text-white disabled:opacity-50"
            disabled={state.saving}
            onClick={() => void onApplyQnh()}
          >
            Apply QNH
          </button>
          <button
            type="button"
            className="rounded border border-atc-border px-1.5 py-0.5 font-mono text-[10px] text-atc-text-muted hover:bg-atc-surface-elevated disabled:opacity-50"
            disabled={state.saving}
            onClick={() => void onClearQnh()}
          >
            Clear QNH
          </button>
        </div>
      )}
      {!editable && current.qnhAvailable && current.qnhHpa != null && (
        <div className="mb-1 font-mono text-[10px] text-atc-text-muted">
          QNH {current.qnhHpa} hPa
        </div>
      )}
      <pre className="whitespace-pre-wrap font-mono text-[10px] leading-snug text-atc-text-muted">
        {current.text}
      </pre>
      {state.error !== "" && (
        <p className="mt-1 text-atc-danger">{state.error}</p>
      )}
    </div>
  );
};

export default AtisPanel;
