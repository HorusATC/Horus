"use client";
import React, { createContext, useContext, useState, ReactNode } from "react";

export interface Coordinate {
  latitude: number;
  longitude: number;
}

export interface CenterCoordinateContextType {
  centerCoordinate: Coordinate;
  setCenterCoordinate: React.Dispatch<React.SetStateAction<Coordinate>>;
}

export const CenterCoordinateContext = createContext<
  CenterCoordinateContextType | undefined
>(undefined);

export const CenterCoordinateProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  // state定義はシンプルに定義し、直接プロバイダに渡す
  // RJTT (Haneda): frames the default STAR quickstart IFs inside the 400 km range.
  const [centerCoordinate, setCenterCoordinate] = useState<Coordinate>({
    latitude: 35.5494,
    longitude: 139.7798,
  });

  return (
    <CenterCoordinateContext.Provider
      value={{ centerCoordinate, setCenterCoordinate }}
    >
      {children}
    </CenterCoordinateContext.Provider>
  );
};

export const useCenterCoordinate = () => {
  const context = useContext(CenterCoordinateContext);
  if (!context) {
    throw new Error(
      "useCenterCoordinate must be used within a CenterCoordinateProvider"
    );
  }
  return context;
};
