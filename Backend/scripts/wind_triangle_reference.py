#!/usr/bin/env python3
"""Independent wind-triangle reference (PHAK). Used to cross-check Java WindTriangle tests."""

from __future__ import annotations

import math


def normalize_angle(deg: float) -> float:
    return deg % 360.0


def ground_velocity(
    heading_deg: float, tas_kt: float, wind_from_deg: float | None, wind_speed_kt: float
) -> tuple[float, float]:
    if wind_from_deg is None or wind_speed_kt <= 0.0 or (tas_kt == 0.0 and wind_speed_kt == 0.0):
        return tas_kt, normalize_angle(heading_deg)
    h = math.radians(heading_deg)
    tas_e = tas_kt * math.sin(h)
    tas_n = tas_kt * math.cos(h)
    f = math.radians(wind_from_deg)
    wind_e = -wind_speed_kt * math.sin(f)
    wind_n = -wind_speed_kt * math.cos(f)
    gs_e = tas_e + wind_e
    gs_n = tas_n + wind_n
    gs = math.hypot(gs_e, gs_n)
    if gs < 1e-9:
        return 0.0, normalize_angle(heading_deg)
    track = normalize_angle(math.degrees(math.atan2(gs_e, gs_n)))
    return gs, track


def wind_correction_angle_deg(
    course_deg: float, tas_kt: float, wind_from_deg: float, wind_speed_kt: float
) -> float:
    if wind_speed_kt <= 0.0:
        return 0.0
    tas = max(tas_kt, 1e-6)
    arg = (wind_speed_kt / tas) * math.sin(math.radians(wind_from_deg - course_deg))
    arg = max(-1.0, min(1.0, arg))
    return math.degrees(math.asin(arg))


def main() -> None:
    # PHAK Chapter 16 style: heading 090, TAS 120
    gs_tail, track_tail = ground_velocity(90.0, 120.0, 270.0, 20.0)
    gs_head, track_head = ground_velocity(90.0, 120.0, 90.0, 20.0)
    gs_cross, track_cross = ground_velocity(90.0, 120.0, 0.0, 20.0)
    wca = wind_correction_angle_deg(90.0, 120.0, 0.0, 20.0)
    print(f"tailwind GS={gs_tail:.4f} track={track_tail:.4f}")
    print(f"headwind GS={gs_head:.4f} track={track_head:.4f}")
    print(f"crosswind GS={gs_cross:.4f} track={track_cross:.4f}")
    print(f"WCA course090 wind000/20={wca:.4f}")
    assert abs(gs_tail - 140.0) < 0.1
    assert abs(track_tail - 90.0) < 0.1
    assert abs(gs_head - 100.0) < 0.1
    assert abs(track_head - 90.0) < 0.1
    assert abs(gs_cross - math.hypot(120.0, 20.0)) < 0.1
    assert abs(wca - math.degrees(math.asin(-20.0 / 120.0))) < 0.1
    print("OK")


if __name__ == "__main__":
    main()
