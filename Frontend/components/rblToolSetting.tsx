"use client";
import React from "react";
import { useRblTool } from "@/context/rblToolContext";

const RblToolSetting = ({ embedded = false }: { embedded?: boolean }) => {
  const { toolEnabled, setToolEnabled, clearRbl, activeRbl, pendingStart } =
    useRblTool();
  const hasLine = activeRbl != null || pendingStart != null;

  const content = (
    <>
      <div className="mb-2 flex items-center justify-between">
        <label className="font-mono text-xs font-bold tracking-wider text-atc-text">
          RBL:
        </label>
        <label className="flex cursor-pointer items-center gap-2">
          <input
            type="checkbox"
            checked={toolEnabled}
            onChange={(e) => setToolEnabled(e.target.checked)}
            className="rounded border-atc-border bg-atc-surface-elevated text-atc-accent focus:ring-atc-accent"
          />
          <span className="text-xs text-atc-text">ツール</span>
        </label>
      </div>
      <div className="flex items-center justify-between gap-2">
        <span className="font-mono text-xs text-atc-text-muted">
          {toolEnabled
            ? pendingStart
              ? "終点をクリック"
              : "始点をクリック"
            : hasLine
              ? "線あり（解除まで残る）"
              : "オフ"}
        </span>
        <button
          type="button"
          className="rounded border border-atc-border px-2 py-1 font-mono text-xs text-atc-text hover:border-atc-accent disabled:opacity-40"
          disabled={!hasLine}
          onClick={() => clearRbl()}
        >
          解除
        </button>
      </div>
    </>
  );

  if (embedded) {
    return content;
  }
  return (
    <div className="mb-3 rounded-lg border border-atc-border bg-atc-surface p-3">
      {content}
    </div>
  );
};

export default RblToolSetting;
