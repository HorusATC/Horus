"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  loadScenarioAndStart,
  parseScenarioJson,
} from "@/utility/api/scenario";
import quickstartSample from "@/data/quickstart-arrival-sample.json";
import type { ScenarioJson } from "@/types/scenario";

export default function Home() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleStartWithSample = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await loadScenarioAndStart(
        quickstartSample as ScenarioJson
      );
      if (!result.ok) {
        setError(result.message);
        setLoading(false);
        return;
      }
      const startResponse = await fetch("/api/simulation/start", {
        method: "POST",
      });
      if (!startResponse.ok) {
        setError("Failed to start simulation");
        setLoading(false);
        return;
      }
      router.push("/controller");
    } catch (e) {
      setError(String(e));
      setLoading(false);
    }
  };

  const handleFileUpload = async (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setLoading(true);
    setError(null);
    try {
      const text = await file.text();
      const scenario = parseScenarioJson(text);
      const result = await loadScenarioAndStart(scenario);
      if (!result.ok) {
        setError(result.message);
        setLoading(false);
        return;
      }
      const startResponse = await fetch("/api/simulation/start", {
        method: "POST",
      });
      if (!startResponse.ok) {
        setError("Failed to start simulation");
        setLoading(false);
        return;
      }
      router.push("/controller");
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-atc-bg text-atc-text flex flex-col items-center justify-center p-8">
      <div className="max-w-2xl w-full space-y-10">
        <header className="text-center space-y-4">
          <h1 className="font-mono text-4xl sm:text-5xl font-bold tracking-[0.25em] text-atc-text">
            HORUS
          </h1>
          <p className="text-atc-text-muted text-sm sm:text-base leading-relaxed max-w-md mx-auto">
            ATC radar simulation for training. Controller speaks instructions;
            Operator reads back and sends them to move aircraft.
          </p>
        </header>

        <div className="space-y-4">
          <button
            onClick={handleStartWithSample}
            disabled={loading}
            className="w-full rounded-lg p-6 bg-atc-accent text-white font-mono font-bold tracking-wider
                       transition-colors duration-200
                       hover:bg-atc-accent-hover focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg
                       disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {loading ? "LOADING..." : "START WITH SAMPLE (サンプルで始める)"}
          </button>

          <div className="relative">
            <input
              type="file"
              accept=".json"
              onChange={handleFileUpload}
              disabled={loading}
              className="absolute inset-0 w-full h-full opacity-0 cursor-pointer disabled:cursor-not-allowed"
              id="file-upload"
            />
            <label
              htmlFor="file-upload"
              className="block w-full rounded-lg p-4 bg-atc-surface border border-atc-border text-center
                         transition-colors duration-200
                         hover:border-atc-accent cursor-pointer"
            >
              <span className="text-sm font-mono text-atc-text">
                LOAD SCENARIO JSON FILE
              </span>
            </label>
          </div>

          {error && (
            <div className="rounded-lg p-4 bg-red-900/20 border border-red-700/50">
              <p className="text-sm text-red-300 font-mono">{error}</p>
            </div>
          )}
        </div>

        <nav className="grid gap-4 sm:grid-cols-2">
          <Link
            href="/controller"
            className="group block rounded-lg p-6 bg-atc-surface border border-atc-border
                       transition-colors duration-200 animate-fade-in
                       hover:border-atc-accent focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg"
            style={{ animationDelay: "100ms" }}
          >
            <h2 className="font-mono text-lg font-bold tracking-wider text-atc-text mb-2">
              CONTROLLER (管制官役)
            </h2>
            <p className="text-sm text-atc-text-muted leading-relaxed">
              View radar and give verbal instructions. Record your issued
              instructions as memos. No direct aircraft control.
            </p>
            <span className="inline-block mt-3 text-xs font-mono text-atc-accent group-hover:text-atc-accent-hover transition-colors">
              Go to Controller →
            </span>
          </Link>

          <Link
            href="/operator"
            className="group block rounded-lg p-6 bg-atc-surface border border-atc-border
                       transition-colors duration-200 animate-fade-in
                       hover:border-atc-accent focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg"
            style={{ animationDelay: "200ms" }}
          >
            <h2 className="font-mono text-lg font-bold tracking-wider text-atc-text mb-2">
              OPERATOR (パイロット役)
            </h2>
            <p className="text-sm text-atc-text-muted leading-relaxed">
              Listen, read back, and input instructions. Send altitude, speed,
              heading, Direct To, and Resume to control aircraft.
            </p>
            <span className="inline-block mt-3 text-xs font-mono text-atc-accent group-hover:text-atc-accent-hover transition-colors">
              Go to Operator →
            </span>
          </Link>

          <Link
            href="/flight-plan-setup"
            className="group block rounded-lg p-6 bg-atc-surface border border-atc-border
                       transition-colors duration-200 animate-fade-in
                       hover:border-atc-accent focus:outline-none focus:ring-2 focus:ring-atc-accent focus:ring-offset-2 focus:ring-offset-atc-bg sm:col-span-2"
            style={{ animationDelay: "300ms" }}
          >
            <h2 className="font-mono text-lg font-bold tracking-wider text-atc-text mb-2">
              FLIGHT PLAN SETUP
            </h2>
            <p className="text-sm text-atc-text-muted leading-relaxed">
              Create Haneda samples, define routes by O/D pair, assign flight
              plans to multiple aircraft in bulk.
            </p>
            <span className="inline-block mt-3 text-xs font-mono text-atc-accent group-hover:text-atc-accent-hover transition-colors">
              Go to Flight Plan Setup →
            </span>
          </Link>
        </nav>
      </div>
    </div>
  );
}
