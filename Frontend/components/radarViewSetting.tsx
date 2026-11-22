"use client";
import React, { useState } from "react";
import CollapsiblePanel from "@/components/ui/collapsiblePanel";
import SectorSelector from "@/components/sectorSelector";
import DisplayRangeSetting from "@/components/displayRangeSetting";
import RangeRingsSetting from "@/components/rangeRingsSetting";
import RblToolSetting from "@/components/rblToolSetting";
import VelocityVectorLookaheadSetting from "@/components/velocityVectorLookaheadSetting";
import { useDisplayRange } from "@/context/displayRangeContext";
import { useRangeRingsSetting } from "@/context/rangeRingsSettingContext";
import { useRblTool } from "@/context/rblToolContext";
import { useVelocityVectorLookahead } from "@/context/velocityVectorLookaheadContext";

const RadarViewSetting = () => {
  // Label only until the user picks a sector; initial radar center is RJTT (see CenterCoordinateProvider).
  const [selectedSector, setSelectedSector] = useState("RJTT");
  const { displayRange } = useDisplayRange();
  const { rangeRingsSetting } = useRangeRingsSetting();
  const { toolEnabled: rblToolEnabled } = useRblTool();
  const { durationMinutes } = useVelocityVectorLookahead();

  const rangeSummary = rangeRingsSetting.enabled
    ? `${rangeRingsSetting.intervalNm}NM`
    : "オフ";
  const rblSummary = rblToolEnabled ? "RBLオン" : "RBLオフ";
  const summary = `${selectedSector}, ${displayRange.range}km, レンジ${rangeSummary}, 予測時間${durationMinutes}分, ${rblSummary}`;

  return (
    <CollapsiblePanel title="レーダー表示" summary={summary}>
      <div className="space-y-3">
        <SectorSelector
          embedded
          value={selectedSector}
          onChange={setSelectedSector}
        />
        <DisplayRangeSetting embedded />
        <VelocityVectorLookaheadSetting embedded />
        <RangeRingsSetting embedded />
        <RblToolSetting embedded />
      </div>
    </CollapsiblePanel>
  );
};

export default RadarViewSetting;
