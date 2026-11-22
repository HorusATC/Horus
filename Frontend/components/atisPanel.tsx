"use client";

import React, { useEffect, useState } from "react";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";
import { fetchAtisAll, updateAtis, type AtisState } from "@/utility/api/atis";

type AtisPanelProps = {
  editable: boolean;
};

const DEFAULT_AIRPORT = "RJTT";

const AtisPanel: React.FC<AtisPanelProps> = ({ editable }) => {
  const [states, setStates] = useState<AtisState[]>([]);
  const [selectedIcao, setSelectedIcao] = useState(DEFAULT_AIRPORT);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

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

  const onRunwayChange = async (runwayInUse: string) => {
    if (!editable || current == null || runwayInUse === current.runwayInUse) {
      return;
    }
    setSaving(true);
    setError("");
    const result = await updateAtis(current.airportIcao, { runwayInUse });
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
      <pre className="whitespace-pre-wrap font-mono text-[10px] leading-snug text-atc-text-muted">
        {current.text}
      </pre>
      {error !== "" && <p className="mt-1 text-atc-danger">{error}</p>}
    </div>
  );
};

export default AtisPanel;
