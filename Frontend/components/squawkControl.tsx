"use client";

import React, { useState, useEffect } from "react";
import { useSelectedAircraft } from "@/context/selectedAircraftContext";
import { setSquawk } from "@/utility/api/squawk";
import {
  EMERGENCY_SQUAWKS,
  type EmergencySquawkCode,
} from "@/utility/aircraft/emergencySquawk";

const EMERGENCY_SQUAWK_UI = [
  {
    code: "7500" as EmergencySquawkCode,
    label: "7500 HIJK",
    color: "bg-red-600 hover:bg-red-700",
  },
  {
    code: "7600" as EmergencySquawkCode,
    label: "7600 RDOF",
    color: "bg-orange-600 hover:bg-orange-700",
  },
  {
    code: "7700" as EmergencySquawkCode,
    label: "7700 EMRG",
    color: "bg-red-600 hover:bg-red-700",
  },
] as const;

const NORMAL_SQUAWK = "1200";

const SquawkControl = () => {
  const { callsign } = useSelectedAircraft();
  const [squawkInput, setSquawkInput] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    setSquawkInput("");
    setError(null);
    setSuccess(false);
  }, [callsign]);

  const validateSquawk = (value: string): boolean => {
    return /^[0-7]{4}$/.test(value);
  };

  const handleSetSquawk = async (squawk: string) => {
    if (!callsign || callsign.length < 2) {
      setError("No aircraft selected");
      return;
    }

    if (!validateSquawk(squawk)) {
      setError("Squawk must be 4 octal digits (0-7)");
      return;
    }

    setError(null);
    setSuccess(false);
    setIsSubmitting(true);

    try {
      const ok = await setSquawk(callsign, squawk);
      if (ok) {
        setSuccess(true);
        setTimeout(() => setSuccess(false), 2000);
      } else {
        setError("Failed to set squawk");
      }
    } catch {
      setError("Network error");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleInputSubmit = () => {
    if (squawkInput.trim().length === 4) {
      void handleSetSquawk(squawkInput.trim());
    }
  };

  const handleEmergencySquawk = (code: string) => {
    setSquawkInput(code);
    void handleSetSquawk(code);
  };

  const handleClearSquawk = () => {
    setSquawkInput(NORMAL_SQUAWK);
    void handleSetSquawk(NORMAL_SQUAWK);
  };

  if (!callsign || callsign.length < 2) {
    return (
      <div className="bg-atc-surface border border-atc-border rounded-lg p-4">
        <h3 className="text-sm font-bold text-atc-text font-mono tracking-wider mb-3">
          SQUAWK
        </h3>
        <p className="text-sm text-atc-text-muted">
          Select aircraft to set squawk code
        </p>
      </div>
    );
  }

  return (
    <div className="bg-atc-surface border border-atc-border rounded-lg p-4">
      <h3 className="text-sm font-bold text-atc-text font-mono tracking-wider mb-3">
        SQUAWK
      </h3>

      <div className="space-y-4">
        <div className="group relative">
          <label
            htmlFor="squawk-input"
            className="block text-xs font-semibold text-atc-text-muted mb-1"
          >
            Squawk Code (4 digits, 0-7)
          </label>
          <div className="flex gap-2">
            <input
              type="text"
              id="squawk-input"
              placeholder="0000"
              value={squawkInput}
              onChange={(e) => {
                const value = e.target.value.replace(/[^0-7]/g, "").slice(0, 4);
                setSquawkInput(value);
                setError(null);
                setSuccess(false);
              }}
              maxLength={4}
              className="flex-1 px-3 py-2 bg-atc-surface-elevated border border-atc-border rounded
                         text-atc-text text-sm font-mono placeholder-atc-text-muted
                         focus:outline-none focus:border-atc-accent
                         hover:border-atc-text-muted"
            />
            <button
              type="button"
              onClick={handleInputSubmit}
              disabled={isSubmitting || squawkInput.length !== 4}
              className="px-4 py-2 bg-atc-accent text-white font-bold text-sm
                         rounded-lg border border-transparent
                         transition-colors duration-200
                         hover:bg-atc-accent-hover
                         focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg
                         disabled:opacity-50 disabled:pointer-events-none"
            >
              SET
            </button>
          </div>
        </div>

        <div className="space-y-2">
          <p className="text-xs font-semibold text-atc-text-muted">
            Emergency Codes
          </p>
          <div className="grid grid-cols-1 gap-2">
            {EMERGENCY_SQUAWK_UI.map(({ code, label, color }) => (
              <button
                key={code}
                type="button"
                onClick={() => handleEmergencySquawk(code)}
                disabled={isSubmitting}
                className={`w-full px-4 py-2 text-white font-bold text-sm
                           rounded-lg border border-transparent
                           transition-colors duration-200
                           focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-atc-bg
                           disabled:opacity-50 disabled:pointer-events-none
                           ${color}`}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <button
          type="button"
          onClick={handleClearSquawk}
          disabled={isSubmitting}
          className="w-full px-4 py-2 bg-atc-surface-elevated text-atc-text font-bold text-sm
                     rounded-lg border border-atc-border
                     transition-colors duration-200
                     hover:bg-atc-bg hover:border-atc-text-muted
                     focus:outline-none focus:ring-2 focus:ring-atc-text-muted focus:ring-offset-2 focus:ring-offset-atc-bg
                     disabled:opacity-50 disabled:pointer-events-none"
        >
          CLEAR (1200)
        </button>

        {error && (
          <p className="text-xs text-red-400 font-mono" role="alert">
            {error}
          </p>
        )}

        {success && (
          <p className="text-xs text-green-400 font-mono">
            Squawk set successfully
          </p>
        )}
      </div>
    </div>
  );
};

export default SquawkControl;
