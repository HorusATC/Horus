"use client";

import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import AtisPanel from "./atisPanel";
import * as atisApi from "@/utility/api/atis";
import { GLOBAL_CONSTANTS } from "@/utility/globals/constants";

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

async function flushMicrotasks() {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
}

describe("atisPanel repro (inverted PR-B)", () => {
  beforeEach(() => {
    jest.useRealTimers();
    (atisApi.fetchAtisAll as jest.Mock).mockReset();
    (atisApi.updateAtis as jest.Mock).mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("claim1: same-value poll with new object keeps unsaved wind/QNH draft", async () => {
    jest.useFakeTimers();
    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...sample }]);
    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });

    render(<AtisPanel editable />);
    await flushMicrotasks();
    await waitFor(() => {
      expect(screen.getByLabelText(/Wind speed knots/i)).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText(/Wind direction degrees/i), "160");
    await user.type(screen.getByLabelText(/Wind speed knots/i), "12");
    await user.type(screen.getByLabelText(/QNH hectopascals/i), "1009");

    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...sample }]);
    act(() => {
      jest.advanceTimersByTime(GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL);
    });
    await flushMicrotasks();

    expect(screen.getByLabelText(/Wind direction degrees/i)).toHaveValue("160");
    expect(screen.getByLabelText(/Wind speed knots/i)).toHaveValue("12");
    expect(screen.getByLabelText(/QNH hectopascals/i)).toHaveValue("1009");
  });

  it("claim10a: delayed GET after runway PUT success does not revert to 16R/ALPHA", async () => {
    jest.useFakeTimers();
    let resolveDelayedPoll: ((value: atisApi.AtisState[]) => void) | null =
      null;
    (atisApi.fetchAtisAll as jest.Mock)
      .mockResolvedValueOnce([{ ...sample }])
      .mockImplementationOnce(
        () =>
          new Promise<atisApi.AtisState[]>((resolve) => {
            resolveDelayedPoll = resolve;
          })
      );

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

    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    render(<AtisPanel editable />);
    await flushMicrotasks();
    await waitFor(() => {
      expect(screen.getByLabelText(/Runway in use/i)).toHaveValue("16R");
    });

    act(() => {
      jest.advanceTimersByTime(GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL);
    });
    expect(resolveDelayedPoll).not.toBeNull();

    await user.selectOptions(screen.getByLabelText(/Runway in use/i), "34L");
    await flushMicrotasks();
    await waitFor(() => {
      expect(screen.getByText(/INFORMATION BRAVO/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Runway in use/i)).toHaveValue("34L");
    });

    act(() => {
      resolveDelayedPoll?.([{ ...sample }]);
    });
    await flushMicrotasks();

    expect(screen.getByText(/INFORMATION BRAVO/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Runway in use/i)).toHaveValue("34L");
  });

  it("claim10b: wind PUT sends expectedRevision; 409 keeps displayed runway from conflict body", async () => {
    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...sample }]);
    (atisApi.updateAtis as jest.Mock).mockResolvedValue({
      ok: false,
      conflict: true,
      message: "ATIS revision mismatch",
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
    await waitFor(() => {
      expect(
        screen.getByText(/ATIS changed by another controller/i)
      ).toBeInTheDocument();
      expect(screen.getByLabelText(/Runway in use/i)).toHaveValue("34L");
      expect(screen.getByText(/INFORMATION BRAVO/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Wind direction degrees/i)).toHaveValue(
        "160"
      );
      expect(screen.getByLabelText(/Wind speed knots/i)).toHaveValue("12");
    });
  });

  it("decision4: decimal wind/QNH inputs are rejected with range messages and do not call updateAtis", async () => {
    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...sample }]);
    const user = userEvent.setup();
    render(<AtisPanel editable />);
    await waitFor(() => {
      expect(screen.getByLabelText(/Wind speed knots/i)).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText(/Wind direction degrees/i), "160.9");
    await user.type(screen.getByLabelText(/Wind speed knots/i), "12.7");
    await user.click(screen.getByRole("button", { name: /Apply wind/i }));
    await waitFor(() => {
      expect(
        screen.getByText(/Wind speed must be 0–99 kt/i)
      ).toBeInTheDocument();
    });
    expect(atisApi.updateAtis).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText(/QNH hectopascals/i));
    await user.type(screen.getByLabelText(/QNH hectopascals/i), "1013.6");
    await user.click(screen.getByRole("button", { name: /Apply QNH/i }));
    await waitFor(() => {
      expect(screen.getByText(/QNH must be 850–1100 hPa/i)).toBeInTheDocument();
    });
    expect(atisApi.updateAtis).not.toHaveBeenCalled();
  });

  it("combination: draft survives changed server-wind poll, apply succeeds, later poll keeps applied values", async () => {
    jest.useFakeTimers();
    const withServerWind: atisApi.AtisState = {
      ...sample,
      revision: 1,
      informationIdent: "B",
      windAvailable: true,
      windDirectionDeg: 90,
      windSpeedKt: 5,
      text: sample.text
        .replaceAll("ALPHA", "BRAVO")
        .replace(
          "WIND AND WEATHER NOT AVAILABLE.",
          "WIND 090 DEGREES 5 KNOTS.\nWEATHER NOT AVAILABLE."
        ),
    };
    const applied: atisApi.AtisState = {
      ...sample,
      revision: 2,
      informationIdent: "C",
      windAvailable: true,
      windDirectionDeg: 160,
      windSpeedKt: 12,
      text: sample.text
        .replaceAll("ALPHA", "CHARLIE")
        .replace(
          "WIND AND WEATHER NOT AVAILABLE.",
          "WIND 160 DEGREES 12 KNOTS.\nWEATHER NOT AVAILABLE."
        ),
    };

    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...sample }]);
    (atisApi.updateAtis as jest.Mock).mockResolvedValue({
      ok: true,
      state: applied,
    });

    const user = userEvent.setup({ advanceTimers: jest.advanceTimersByTime });
    render(<AtisPanel editable />);
    await flushMicrotasks();
    await waitFor(() => {
      expect(screen.getByLabelText(/Wind speed knots/i)).toBeInTheDocument();
    });

    await user.type(screen.getByLabelText(/Wind direction degrees/i), "160");
    await user.type(screen.getByLabelText(/Wind speed knots/i), "12");

    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([
      { ...withServerWind },
    ]);
    act(() => {
      jest.advanceTimersByTime(GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL);
    });
    await flushMicrotasks();

    expect(screen.getByLabelText(/Wind direction degrees/i)).toHaveValue("160");
    expect(screen.getByLabelText(/Wind speed knots/i)).toHaveValue("12");
    expect(screen.getByText(/INFORMATION BRAVO/i)).toBeInTheDocument();
    expect(screen.getByText(/WIND 090 DEGREES 5 KNOTS/i)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Apply wind/i }));
    await flushMicrotasks();
    await waitFor(() => {
      expect(atisApi.updateAtis).toHaveBeenCalledWith("RJTT", {
        runwayInUse: "16R",
        wind: { directionDeg: 160, speedKt: 12 },
        expectedRevision: 1,
      });
      expect(screen.getByText(/INFORMATION CHARLIE/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/Wind direction degrees/i)).toHaveValue(
        "160"
      );
      expect(screen.getByLabelText(/Wind speed knots/i)).toHaveValue("12");
    });

    (atisApi.fetchAtisAll as jest.Mock).mockResolvedValue([{ ...applied }]);
    act(() => {
      jest.advanceTimersByTime(GLOBAL_CONSTANTS.LOCATION_UPDATE_INTERVAL);
    });
    await flushMicrotasks();

    expect(screen.getByText(/INFORMATION CHARLIE/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Wind direction degrees/i)).toHaveValue("160");
    expect(screen.getByLabelText(/Wind speed knots/i)).toHaveValue("12");
  });
});
