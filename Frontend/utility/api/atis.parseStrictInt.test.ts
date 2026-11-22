import {
  parseStrictNonNegativeInt,
  QNH_HPA_LIMITS,
  WIND_DIRECTION_DEG_LIMITS,
  WIND_SPEED_KT_LIMITS,
} from "./atis";

describe("parseStrictNonNegativeInt", () => {
  const speedMessage = `Wind speed must be ${WIND_SPEED_KT_LIMITS.min}–${WIND_SPEED_KT_LIMITS.max} kt`;
  const directionMessage = `Wind direction must be ${WIND_DIRECTION_DEG_LIMITS.min}–${WIND_DIRECTION_DEG_LIMITS.max} deg`;
  const qnhMessage = `QNH must be ${QNH_HPA_LIMITS.min}–${QNH_HPA_LIMITS.max} hPa`;

  it("accepts digit-only integers in range", () => {
    expect(
      parseStrictNonNegativeInt(
        "12",
        WIND_SPEED_KT_LIMITS.min,
        WIND_SPEED_KT_LIMITS.max,
        speedMessage
      )
    ).toEqual({ ok: true, value: 12 });
    expect(
      parseStrictNonNegativeInt(
        "0",
        WIND_SPEED_KT_LIMITS.min,
        WIND_SPEED_KT_LIMITS.max,
        speedMessage
      )
    ).toEqual({ ok: true, value: 0 });
    expect(
      parseStrictNonNegativeInt(
        "160",
        WIND_DIRECTION_DEG_LIMITS.min,
        WIND_DIRECTION_DEG_LIMITS.max,
        directionMessage
      )
    ).toEqual({ ok: true, value: 160 });
    expect(
      parseStrictNonNegativeInt(
        "1013",
        QNH_HPA_LIMITS.min,
        QNH_HPA_LIMITS.max,
        qnhMessage
      )
    ).toEqual({ ok: true, value: 1013 });
  });

  it("rejects decimals without truncating", () => {
    expect(
      parseStrictNonNegativeInt("160.9", 1, 360, directionMessage)
    ).toEqual({ ok: false, message: directionMessage });
    expect(parseStrictNonNegativeInt("12.7", 0, 99, speedMessage)).toEqual({
      ok: false,
      message: speedMessage,
    });
    expect(parseStrictNonNegativeInt("1013.6", 850, 1100, qnhMessage)).toEqual({
      ok: false,
      message: qnhMessage,
    });
  });

  it("rejects empty, signed, and exponential forms", () => {
    expect(parseStrictNonNegativeInt("", 0, 99, speedMessage)).toEqual({
      ok: false,
      message: speedMessage,
    });
    expect(parseStrictNonNegativeInt("-1", 0, 99, speedMessage)).toEqual({
      ok: false,
      message: speedMessage,
    });
    expect(parseStrictNonNegativeInt("1e2", 0, 99, speedMessage)).toEqual({
      ok: false,
      message: speedMessage,
    });
  });
});
