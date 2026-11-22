"use client";

import React, {
  createContext,
  useMemo,
  useContext,
  useState,
  type ReactNode,
} from "react";

export interface isSelectFixMode {
  selectFixMode: boolean;
}

export interface SelectedFixGeo {
  latitude: number;
  longitude: number;
}

export interface SelectFixModeContextType {
  isSelectFixMode: isSelectFixMode;
  setIsSelectFixMode: React.Dispatch<React.SetStateAction<isSelectFixMode>>;
  selectedFixName: string;
  setSelectedFixName: (name: string) => void;
  selectedFixGeo: SelectedFixGeo | null;
  setSelectedFixGeo: (geo: SelectedFixGeo | null) => void;
}

export const SelectFixModeContext = createContext<
  SelectFixModeContextType | undefined
>(undefined);

const DEFAULT_FIX_NAME = "No fixes selected";

export const SelectFixModeProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [isSelectFixMode, setIsSelectFixMode] = useState<isSelectFixMode>({
    selectFixMode: false,
  });
  const [selectedFixName, setSelectedFixName] = useState(DEFAULT_FIX_NAME);
  const [selectedFixGeo, setSelectedFixGeo] = useState<SelectedFixGeo | null>(
    null
  );

  const value = useMemo(
    () => ({
      isSelectFixMode,
      setIsSelectFixMode,
      selectedFixName,
      setSelectedFixName,
      selectedFixGeo,
      setSelectedFixGeo,
    }),
    [isSelectFixMode, selectedFixName, selectedFixGeo]
  );

  return (
    <SelectFixModeContext.Provider value={value}>
      {children}
    </SelectFixModeContext.Provider>
  );
};

export const useSelectFixMode = () => {
  const context = useContext(SelectFixModeContext);
  if (!context) {
    throw new Error(
      "useSelectFixMode must be used within a SelectFixModeProvider"
    );
  }
  return context;
};
