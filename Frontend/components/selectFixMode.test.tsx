"use client";

import React, { useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SelectFixMode from "./selectFixMode";
import {
  SelectFixModeProvider,
  useSelectFixMode,
} from "@/context/selectFixModeContext";
import {
  SelectedAircraftProvider,
  useSelectedAircraft,
} from "@/context/selectedAircraftContext";
import * as flightPlanApi from "@/utility/api/flightPlan";
import type { HoldAtFixParams } from "@/utility/api/flightPlan";

function StateSetter({ callsign }: { callsign: string | null }) {
  const { setCallsign } = useSelectedAircraft();
  useEffect(() => {
    setCallsign(callsign);
  }, [callsign, setCallsign]);
  return null;
}

function FixModeActivator({ fixName }: { fixName: string }) {
  const { setSelectedFixName, setIsSelectFixMode } = useSelectFixMode();
  useEffect(() => {
    setSelectedFixName(fixName);
    setIsSelectFixMode({ selectFixMode: true });
  }, [fixName, setSelectedFixName, setIsSelectFixMode]);
  return null;
}

beforeEach(() => {
  jest.clearAllMocks();
});

jest.mock("@/utility/api/flightPlan");

function renderWithProviders(callsign: string | null) {
  return render(
    <SelectedAircraftProvider>
      <SelectFixModeProvider>
        <StateSetter callsign={callsign} />
        <SelectFixMode />
      </SelectFixModeProvider>
    </SelectedAircraftProvider>
  );
}

describe("SelectFixMode", () => {
  it("shows No fixes selected by default", () => {
    renderWithProviders("JAL123");

    expect(screen.getByText(/No fixes selected/i)).toBeInTheDocument();
  });

  it("shows DIRECT TO FIX button when not in fix mode", () => {
    renderWithProviders("JAL123");

    expect(
      screen.getByRole("button", { name: /DIRECT TO FIX/i })
    ).toBeInTheDocument();
  });

  it("shows Select aircraft first when clicking without callsign", async () => {
    const user = userEvent.setup();
    renderWithProviders(null);

    await user.click(screen.getByRole("button", { name: /DIRECT TO FIX/i }));

    await waitFor(() => {
      expect(screen.getByText(/Select aircraft first/i)).toBeInTheDocument();
    });
  });

  it("enters fix mode and shows CONFIRM/CANCEL when DIRECT TO FIX clicked with callsign", async () => {
    const user = userEvent.setup();
    renderWithProviders("JAL123");

    await user.click(screen.getByRole("button", { name: /DIRECT TO FIX/i }));

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /CONFIRM/i })
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /CANCEL/i })
      ).toBeInTheDocument();
    });
  });

  it("CANCEL exits fix mode and resets selected fix name", async () => {
    const user = userEvent.setup();
    renderWithProviders("JAL123");

    await user.click(screen.getByRole("button", { name: /DIRECT TO FIX/i }));
    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /CANCEL/i })
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /CANCEL/i }));

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /DIRECT TO FIX/i })
      ).toBeInTheDocument();
      expect(screen.getByText(/No fixes selected/i)).toBeInTheDocument();
    });
  });

  it("CONFIRM calls directToFix when direct command is selected", async () => {
    const user = userEvent.setup();
    (flightPlanApi.directToFix as jest.Mock).mockResolvedValue(true);

    render(
      <SelectedAircraftProvider>
        <SelectFixModeProvider>
          <StateSetter callsign="JAL123" />
          <FixModeActivator fixName="ABENO" />
          <SelectFixMode />
        </SelectFixModeProvider>
      </SelectedAircraftProvider>
    );

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /CONFIRM/i })
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /CONFIRM/i }));

    await waitFor(() => {
      expect(flightPlanApi.directToFix).toHaveBeenCalledWith(
        "JAL123",
        "ABENO",
        false
      );
    });
  });

  it("CONFIRM calls holdAtFix when hold command is selected", async () => {
    const user = userEvent.setup();
    (flightPlanApi.holdAtFix as jest.Mock).mockResolvedValue(true);

    render(
      <SelectedAircraftProvider>
        <SelectFixModeProvider>
          <StateSetter callsign="JAL123" />
          <FixModeActivator fixName="ABENO" />
          <SelectFixMode />
        </SelectFixModeProvider>
      </SelectedAircraftProvider>
    );

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /^HOLD$/i })
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /^HOLD$/i }));
    await user.click(screen.getByRole("button", { name: /CONFIRM/i }));

    await waitFor(() => {
      expect(flightPlanApi.holdAtFix).toHaveBeenCalledTimes(1);
      const [, params] = (flightPlanApi.holdAtFix as jest.Mock).mock
        .calls[0] as [string, HoldAtFixParams];
      expect(params.fixName).toBe("ABENO");
      expect(params.turnDirection).toBe("RIGHT");
      expect(typeof params.inboundCourseDeg).toBe("number");
      expect(params.leg?.kind).toBe("minutes");
    });
  });

  it("HOLD form sends LEFT turn and NM leg when selected", async () => {
    const user = userEvent.setup();
    (flightPlanApi.holdAtFix as jest.Mock).mockResolvedValue(true);

    render(
      <SelectedAircraftProvider>
        <SelectFixModeProvider>
          <StateSetter callsign="JAL123" />
          <FixModeActivator fixName="ABENO" />
          <SelectFixMode />
        </SelectFixModeProvider>
      </SelectedAircraftProvider>
    );

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /^HOLD$/i })
      ).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /^HOLD$/i }));
    await user.click(screen.getByRole("button", { name: /^NM$/i }));
    await user.clear(screen.getByLabelText(/Leg \(NM\)/i));
    await user.type(screen.getByLabelText(/Leg \(NM\)/i), "7");
    await user.click(screen.getByRole("button", { name: /^LEFT$/i }));
    await user.clear(screen.getByLabelText(/Inbound course/i));
    await user.type(screen.getByLabelText(/Inbound course/i), "209");
    await user.click(screen.getByRole("button", { name: /CONFIRM/i }));

    await waitFor(() => {
      expect(flightPlanApi.holdAtFix).toHaveBeenCalledWith("JAL123", {
        fixName: "ABENO",
        turnDirection: "LEFT",
        inboundCourseDeg: 209,
        leg: { kind: "nm", legNm: 7 },
      });
    });
  });
});
