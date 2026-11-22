"use client";

import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AtisPanel from "./atisPanel";
import * as atisApi from "@/utility/api/atis";

jest.mock("@/utility/api/atis", () => {
  const actual =
    jest.requireActual<typeof import("@/utility/api/atis")>(
      "@/utility/api/atis"
    );
  return {
    ...actual,
    fetchAtisAll: jest.fn(),
    updateAtis: jest.fn(),
  };
});

const sample: atisApi.AtisState = {
  airportIcao: "RJTT",
  airportName: "Tokyo International Airport",
  runwayInUse: "16R",
  informationIdent: "A",
  text: "Tokyo International Airport (RJTT) INFORMATION ALPHA.\nLANDING AND DEPARTURE RUNWAY 16R.\nWIND AND WEATHER NOT AVAILABLE.\nADVISE YOU HAVE INFORMATION ALPHA.",
  availableRunways: ["16R", "34L", "16L", "34R", "04", "22", "05", "23"],
  windAvailable: false,
  qnhAvailable: false,
  revision: 0,
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
        revision: 1,
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
      expectedRevision: 0,
    });
  });

  it("applies wind with runway unchanged", async () => {
    (atisApi.updateAtis as jest.Mock).mockResolvedValue({
      ok: true,
      state: {
        ...sample,
        informationIdent: "B",
        revision: 1,
        windAvailable: true,
        windDirectionDeg: 160,
        windSpeedKt: 12,
        text: sample.text.replaceAll("ALPHA", "BRAVO"),
      },
    });
    const user = userEvent.setup();
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByLabelText(/Wind speed knots/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/Wind direction degrees/i), "160");
    await user.type(screen.getByLabelText(/Wind speed knots/i), "12");
    await user.click(screen.getByRole("button", { name: /Apply wind/i }));
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        wind: { directionDeg: 160, speedKt: 12 },
        expectedRevision: 0,
      });
    });
  });

  it("sets CALM and clears wind", async () => {
    (atisApi.updateAtis as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        state: {
          ...sample,
          revision: 1,
          windAvailable: true,
          windSpeedKt: 0,
          text: sample.text,
        },
      })
      .mockResolvedValueOnce({
        ok: true,
        state: { ...sample, revision: 2, windAvailable: false },
      });
    const user = userEvent.setup();
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "CALM" })).toBeInTheDocument();
    });
    await user.click(screen.getByRole("button", { name: "CALM" }));
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        wind: { speedKt: 0 },
        expectedRevision: 0,
      });
    });
    await user.click(screen.getByRole("button", { name: "Clear" }));
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        wind: null,
        expectedRevision: 1,
      });
    });
  });

  it("applies and clears QNH", async () => {
    (atisApi.updateAtis as jest.Mock)
      .mockResolvedValueOnce({
        ok: true,
        state: {
          ...sample,
          informationIdent: "B",
          revision: 1,
          qnhAvailable: true,
          qnhHpa: 1013,
          text: sample.text
            .replaceAll("ALPHA", "BRAVO")
            .replace(
              "WIND AND WEATHER NOT AVAILABLE.",
              "WIND NOT AVAILABLE.\nQNH 1013."
            ),
        },
      })
      .mockResolvedValueOnce({
        ok: true,
        state: {
          ...sample,
          informationIdent: "C",
          revision: 2,
          qnhAvailable: false,
        },
      });
    const user = userEvent.setup();
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByLabelText(/QNH hectopascals/i)).toBeInTheDocument();
    });
    await user.type(screen.getByLabelText(/QNH hectopascals/i), "1013");
    await user.click(screen.getByRole("button", { name: /Apply QNH/i }));
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        qnhHpa: 1013,
        expectedRevision: 0,
      });
    });
    await user.click(screen.getByRole("button", { name: /Clear QNH/i }));
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        qnhHpa: null,
        expectedRevision: 1,
      });
    });
  });

  it("is read-only for operator", async () => {
    render(<AtisPanel editable={false} />);
    await waitFor(() => {
      expect(screen.getByText(/INFO A/)).toBeInTheDocument();
    });
    expect(screen.getByText("16R")).toBeInTheDocument();
    expect(screen.queryByLabelText(/Runway in use/i)).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/Wind direction degrees/i)
    ).not.toBeInTheDocument();
    expect(
      screen.queryByLabelText(/QNH hectopascals/i)
    ).not.toBeInTheDocument();
  });
});
