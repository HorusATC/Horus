"use client";

import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AtisPanel from "./atisPanel";
import * as atisApi from "@/utility/api/atis";

jest.mock("@/utility/api/atis");

const sample: atisApi.AtisState = {
  airportIcao: "RJTT",
  airportName: "Tokyo International Airport",
  runwayInUse: "16R",
  informationIdent: "A",
  text: "Tokyo International Airport (RJTT) INFORMATION ALPHA.\nLANDING AND DEPARTURE RUNWAY 16R.\nWIND AND WEATHER NOT AVAILABLE.\nADVISE YOU HAVE INFORMATION ALPHA.",
  availableRunways: ["16R", "34L", "16L", "34R", "04", "22", "05", "23"],
};

describe("AtisPanel", () => {
  beforeEach(() => {
    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([sample]);
  });

  it("shows runway in use and ATIS text for controller", async () => {
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByText(/INFORMATION ALPHA/i)).toBeInTheDocument();
    });
    expect(screen.getByLabelText(/Runway in use/i)).toHaveValue("16R");
  });

  it("updates runway when controller selects reciprocal", async () => {
    (atisApi.updateAtis as jest.Mock).mockResolvedValue({
      ok: true,
      state: {
        ...sample,
        runwayInUse: "34L",
        informationIdent: "B",
        text: sample.text.replaceAll("16R", "34L").replaceAll("ALPHA", "BRAVO"),
      },
    });
    const user = userEvent.setup();
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByLabelText(/Runway in use/i)).toBeInTheDocument();
    });
    await user.selectOptions(screen.getByLabelText(/Runway in use/i), "34L");
    await waitFor(() => {
      expect(screen.getByText(/INFORMATION BRAVO/i)).toBeInTheDocument();
    });
    expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
      runwayInUse: "34L",
    });
  });

  it("is read-only for operator", async () => {
    render(<AtisPanel editable={false} />);
    await waitFor(() => {
      expect(screen.getByText(/INFO A/)).toBeInTheDocument();
    });
    expect(screen.getByText("16R")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Runway in use/i)).not.toBeInTheDocument();
  });
});
