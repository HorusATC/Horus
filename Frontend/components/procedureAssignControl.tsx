"use client";

import React, { useEffect, useMemo, useState } from "react";
import { useSelectedAircraft } from "@/context/selectedAircraftContext";
import {
  assignProcedure,
  fetchProcedures,
  type ProcedureSummary,
} from "@/utility/api/procedures";
import { notifyFlightPlanChanged } from "@/utility/api/flightPlanChanged";

const ProcedureAssignControl: React.FC = () => {
  const { callsign } = useSelectedAircraft();
  const [procedures, setProcedures] = useState<ProcedureSummary[]>([]);
  const [procedureId, setProcedureId] = useState("");
  const [transitionId, setTransitionId] = useState("");
  const [result, setResult] = useState<"idle" | "success" | "error">("idle");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const data = await fetchProcedures("RJTT");
      if (cancelled || data == null) return;
      const assignable = data.procedures.filter((p) => p.assignable);
      setProcedures(assignable);
      const first = assignable[0];
      if (first != null) {
        setProcedureId((current) => current || first.id);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const selected = useMemo(
    () => procedures.find((p) => p.id === procedureId) ?? null,
    [procedures, procedureId]
  );

  const transitions = useMemo(
    () => selected?.enrouteTransitions ?? [],
    [selected]
  );

  useEffect(() => {
    if (!transitions.includes(transitionId)) {
      setTransitionId("");
    }
  }, [transitions, transitionId]);

  const handleAssign = async () => {
    if (!callsign || !procedureId) return;
    setResult("idle");
    setMessage("");
    const response = await assignProcedure(callsign, {
      procedureId,
      transitionId: transitionId || undefined,
      cruiseAltitude: 10000,
      cruiseSpeed: 250,
    });
    if (response?.success) {
      setResult("success");
      const fixes = response.waypoints?.join(" → ") ?? "";
      setMessage(
        response.procedureVaActive
          ? `Assigned ${response.procedureId} (VA ${response.procedureVaCourseTrueDeg}°T). ${fixes}`
          : `Assigned ${response.procedureId}. ${fixes}`
      );
      notifyFlightPlanChanged(callsign);
      setTimeout(() => setResult("idle"), 3000);
    } else {
      setResult("error");
      setMessage("Assign failed");
    }
  };

  if (!callsign) {
    return (
      <div className="bg-atc-surface border border-atc-border rounded-lg p-4 mt-4">
        <p className="text-sm text-atc-text-muted">
          Select aircraft to assign SID / STAR
        </p>
      </div>
    );
  }

  return (
    <div className="bg-atc-surface border border-atc-border rounded-lg p-4 mt-4 space-y-3">
      <div className="text-xs font-semibold text-atc-text">
        SID / STAR ASSIGN
      </div>
      <label className="block text-xs text-atc-text-muted">
        Procedure
        <select
          className="mt-1 w-full px-2 py-1 text-xs font-mono bg-atc-surface-elevated border border-atc-border rounded"
          value={procedureId}
          onChange={(e) => setProcedureId(e.target.value)}
        >
          {procedures.map((p) => (
            <option key={p.id} value={p.id}>
              {p.type} {p.name}
              {p.applicableRunways.length > 0
                ? ` (RWY ${p.applicableRunways.join("/")})`
                : ""}
            </option>
          ))}
        </select>
      </label>
      {transitions.length > 0 && (
        <label className="block text-xs text-atc-text-muted">
          Enroute transition (optional)
          <select
            className="mt-1 w-full px-2 py-1 text-xs font-mono bg-atc-surface-elevated border border-atc-border rounded"
            value={transitionId}
            onChange={(e) => setTransitionId(e.target.value)}
          >
            <option value="">None</option>
            {transitions.map((id) => (
              <option key={id} value={id}>
                {id}
              </option>
            ))}
          </select>
        </label>
      )}
      <button
        type="button"
        className="w-full px-3 py-1.5 text-xs font-semibold bg-atc-accent text-atc-bg rounded hover:opacity-90"
        onClick={() => void handleAssign()}
      >
        ASSIGN PROCEDURE
      </button>
      {result === "success" && (
        <p className="text-xs text-green-400 break-words">{message}</p>
      )}
      {result === "error" && (
        <p className="text-xs text-red-400">{message || "Assign failed"}</p>
      )}
    </div>
  );
};

export default ProcedureAssignControl;
