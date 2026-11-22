"use client";

import React, { useEffect } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import FlightPlanControl from "./flightPlanControl";
import {
  SelectedAircraftProvider,
  useSelectedAircraft,
} from "@/context/selectedAircraftContext";
import * as flightPlanApi from "@/utility/api/flightPlan";
import * as finalApproachApi from "@/utility/api/finalApproach";
import * as atisApi from "@/utility/api/atis";

jest.mock("@/utility/api/flightPlan");
jest.mock("@/utility/api/finalApproach");
jest.mock("@/utility/api/atis");

function StateSetter({ callsign }: { callsign: string | null }) {
  const { setCallsign } = useSelectedAircraft();
  useEffect(() => {
    setCallsign(callsign);
  }, [callsign, setCallsign]);
  return null;
}

describe("FlightPlanControl", () => {
  beforeEach(() => {
    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([
      {
        airportIcao: "RJTT",
        airportName: "Tokyo International Airport",
        runwayInUse: "16R",
        informationIdent: "A",
        text: "ATIS",
        availableRunways: ["16R", "34L", "16L", "34R", "04", "22", "05", "23"],
      },
    ]);
  });

  it("shows 'Select aircraft' when no callsign", () => {
    render(
      <SelectedAircraftProvider>
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    expect(
      screen.getByText(/Select aircraft to issue navigation commands/i)
    ).toBeInTheDocument();
  });

  it("renders guidance and RESUME button when callsign set", () => {
    render(
      <SelectedAircraftProvider>
        <StateSetter callsign="JAL123" />
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    expect(
      screen.getByText(/panel to select a fix and choose direct\/hold/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /RESUME OWN NAVIGATION/i })
    ).toBeInTheDocument();
  });

  it("shows success message when resumeNavigation succeeds", async () => {
    (flightPlanApi.resumeNavigation as jest.Mock).mockResolvedValue(true);
    const user = userEvent.setup();

    render(
      <SelectedAircraftProvider>
        <StateSetter callsign="JAL123" />
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    await user.click(
      screen.getByRole("button", { name: /RESUME OWN NAVIGATION/i })
    );

    await waitFor(() => {
      expect(screen.getByText(/Resume applied/i)).toBeInTheDocument();
    });
  });

  it("shows the backend message when go-around fails", async () => {
    (finalApproachApi.goAround as jest.Mock).mockResolvedValue({
      ok: false,
      message:
        "Go-around climb altitude must be at least 500 ft above current altitude",
    });
    const user = userEvent.setup();

    render(
      <SelectedAircraftProvider>
        <StateSetter callsign="JAL123" />
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    const climbInputs = screen.getAllByPlaceholderText(/Climb altitude ft/i);
    const goAroundClimb = climbInputs[0];
    expect(goAroundClimb).toBeDefined();
    await user.clear(goAroundClimb!);
    await user.type(goAroundClimb!, "2000");
    await user.click(screen.getByRole("button", { name: /GO AROUND/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/at least 500 ft above current altitude/i)
      ).toBeInTheDocument();
    });
    expect(finalApproachApi.goAround).toHaveBeenCalledWith(
      "JAL123",
      "RJTT",
      "16R",
      2000
    );
  });

  it("shows the backend message when depart fails", async () => {
    (finalApproachApi.depart as jest.Mock).mockResolvedValue({
      ok: false,
      message:
        "Departure climb altitude must be at least 500 ft above the start altitude",
    });
    const user = userEvent.setup();

    render(
      <SelectedAircraftProvider>
        <StateSetter callsign="JAL123" />
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    const climbInputs = screen.getAllByPlaceholderText(/Climb altitude ft/i);
    const departClimb = climbInputs[1];
    expect(departClimb).toBeDefined();
    await user.clear(departClimb!);
    await user.type(departClimb!, "400");
    await user.click(screen.getByRole("button", { name: /^DEPART$/i }));

    await waitFor(() => {
      expect(
        screen.getByText(/at least 500 ft above the start altitude/i)
      ).toBeInTheDocument();
    });
    expect(finalApproachApi.depart).toHaveBeenCalledWith(
      "JAL123",
      "RJTT",
      "16R",
      400,
      undefined
    );
  });

  it("shows the backend message when tower handoff fails", async () => {
    (finalApproachApi.handOffToTower as jest.Mock).mockResolvedValue({
      ok: false,
      message: "Establish on the final approach course before tower handoff",
    });
    const user = userEvent.setup();

    render(
      <SelectedAircraftProvider>
        <StateSetter callsign="JAL123" />
        <FlightPlanControl />
      </SelectedAircraftProvider>
    );

    await user.click(
      screen.getByRole("button", { name: /HAND OFF TO TOWER/i })
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Establish on the final approach course/i)
      ).toBeInTheDocument();
    });
    expect(finalApproachApi.handOffToTower).toHaveBeenCalledWith(
      "JAL123",
      "RJTT",
      "16R"
    );
  });
});
