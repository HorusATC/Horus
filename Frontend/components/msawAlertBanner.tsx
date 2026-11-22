"use client";

import React, { useEffect, useState } from "react";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";
import {
  MSAW_ALERTS,
  MSAW_PHRASEOLOGY,
  parseMsawAlert,
  type MsawAlertType,
} from "@/utility/aircraft/msawAlert";

type LocationRow = {
  callsign?: string;
  msawAlert?: { type?: string; msaFt?: number; trigger?: string } | null;
};

const MsawAlertBanner: React.FC = () => {
  const [activeTypes, setActiveTypes] = useState<MsawAlertType[]>([]);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      try {
        const response = await fetch("/api/aircraft/location/all", {
          method: "GET",
          headers: { Accept: "application/json" },
          cache: "no-store",
        });
        if (!response.ok) {
          return;
        }
        const rows: LocationRow[] = await response.json();
        const types = new Set<MsawAlertType>();
        for (const row of rows) {
          const alert = parseMsawAlert(row.msawAlert);
          if (alert != null) {
            types.add(alert.type);
          }
        }
        if (!cancelled) {
          setActiveTypes(Array.from(types));
        }
      } catch {
        // Keep last banner state; location poll errors are logged elsewhere.
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

  if (activeTypes.length === 0) {
    return null;
  }

  const labels = activeTypes.map((type) => MSAW_ALERTS[type].label).join(" / ");

  return (
    <div
      className="pointer-events-none absolute left-3 top-14 z-10 max-w-[min(100%-1.5rem,28rem)] rounded border border-amber-500/70 bg-atc-surface/90 px-3 py-1.5 text-xs text-atc-text shadow-sm backdrop-blur-sm"
      aria-live="polite"
      style={{ color: MSAW_ALERTS.MSA.color }}
    >
      <div className="font-medium">MSAW {labels}</div>
      <div className="text-atc-text">{MSAW_PHRASEOLOGY.ja}</div>
      <div className="text-atc-text-muted">{MSAW_PHRASEOLOGY.en}</div>
    </div>
  );
};

export default MsawAlertBanner;
