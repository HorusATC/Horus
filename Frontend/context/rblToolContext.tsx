"use client";
import React, { createContext, useContext, useMemo, useState } from "react";

export type RblEndpoint =
  | { kind: "aircraft"; callsign: string }
  | { kind: "geo"; latitude: number; longitude: number };

export type RblLine = {
  start: RblEndpoint;
  end: RblEndpoint;
};

type RblToolContextValue = {
  toolEnabled: boolean;
  setToolEnabled: (enabled: boolean) => void;
  pendingStart: RblEndpoint | null;
  activeRbl: RblLine | null;
  beginOrCompleteEndpoint: (endpoint: RblEndpoint) => void;
  clearRbl: () => void;
  handleEscape: () => void;
};

const RblToolContext = createContext<RblToolContextValue | null>(null);

export function RblToolProvider({ children }: { children: React.ReactNode }) {
  const [toolEnabled, setToolEnabled] = useState(false);
  const [pendingStart, setPendingStart] = useState<RblEndpoint | null>(null);
  const [activeRbl, setActiveRbl] = useState<RblLine | null>(null);

  const value = useMemo<RblToolContextValue>(
    () => ({
      toolEnabled,
      setToolEnabled: (enabled: boolean) => {
        setToolEnabled(enabled);
      },
      pendingStart,
      activeRbl,
      beginOrCompleteEndpoint: (endpoint: RblEndpoint) => {
        setPendingStart((current) => {
          if (current == null) {
            return endpoint;
          }
          setActiveRbl({ start: current, end: endpoint });
          return null;
        });
      },
      clearRbl: () => {
        setPendingStart(null);
        setActiveRbl(null);
      },
      handleEscape: () => {
        setPendingStart((current) => {
          if (current != null) {
            return null;
          }
          setActiveRbl(null);
          return null;
        });
      },
    }),
    [toolEnabled, pendingStart, activeRbl]
  );

  return (
    <RblToolContext.Provider value={value}>{children}</RblToolContext.Provider>
  );
}

export function useRblTool(): RblToolContextValue {
  const ctx = useContext(RblToolContext);
  if (ctx == null) {
    throw new Error("useRblTool must be used within RblToolProvider");
  }
  return ctx;
}
