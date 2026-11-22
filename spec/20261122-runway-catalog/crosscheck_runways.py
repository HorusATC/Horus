#!/usr/bin/env python3
"""Cross-check RJTT/RJAA runway catalog values against the AD 2.12 survey table.

Earth radius 6378.1 km (same as Backend AtcSimulatorConstants.EARTH_RADIUS).
Tolerances from spec/20261122-runway-catalog/spec.md:
  - lat/lon vs table: 1e-7 deg
  - heading vs table: 0.01 deg
  - displaced threshold distance: ±2 m
  - opposite-end length vs LDA or pavement length: ±15 m
  - reciprocal headings differ by ~180° (AIP may keep 149.50 / 329.51)
  - parallel pairs share the same TRUE BRG

Values are unofficial AD 2.12 copies + AirBinder AIRAC 2611 — not checked against
the official AIP in force.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

EARTH_RADIUS_KM = 6378.1
LATLON_TOL_DEG = 1e-7
HEADING_TOL_DEG = 0.01
DISPLACED_TOL_M = 2.0
LENGTH_TOL_M = 15.0
PARALLEL_TOL_DEG = 0.01
RECIPROCAL_TOL_DEG = 0.02

REPO_ROOT = Path(__file__).resolve().parents[2]
RUNWAYS_JSON = REPO_ROOT / "Backend" / "src" / "main" / "resources" / "fix" / "runways.json"

# DMS from AD 2.12 (landing threshold unless noted). Decimal columns match the spec table.
SURVEY_TABLE: dict[tuple[str, str], dict] = {
    ("RJTT", "16R"): {
        "heading": 149.88,
        "length_m": 3000,
        "lda_m": 2520,
        "lat_dms": "353322.47N",
        "lon_dms": "1394618.19E",
        "lat": 35.55624167,
        "lon": 139.77171944,
        "phys_lat_dms": "353335.95N",
        "phys_lon_dms": "1394608.64E",
        "displaced_m": 480,
        "usable_for_landing": True,
    },
    ("RJTT", "34L"): {
        "heading": 329.88,
        "length_m": 3000,
        "lda_m": 3000,
        "lat_dms": "353211.76N",
        "lon_dms": "1394708.41E",
        "lat": 35.53660000,
        "lon": 139.78566944,
        "usable_for_landing": True,
    },
    ("RJTT", "16L"): {
        "heading": 149.88,
        "length_m": 3360,
        "lda_m": 2970,
        "lat_dms": "353346.27N",
        "lon_dms": "1394719.34E",
        "lat": 35.56285278,
        "lon": 139.78870556,
        "phys_lat_dms": "353357.23N",
        "phys_lon_dms": "1394711.59E",
        "displaced_m": 390,
        "usable_for_landing": True,
    },
    ("RJTT", "34R"): {
        "heading": 329.88,
        "length_m": 3360,
        "lda_m": 3000,
        "lat_dms": "353233.02N",
        "lon_dms": "1394811.34E",
        "lat": 35.54250556,
        "lon": 139.80315000,
        "phys_lat_dms": "353222.90N",
        "phys_lon_dms": "1394818.49E",
        "displaced_m": 360,
        "usable_for_landing": True,
    },
    ("RJTT", "04"): {
        "heading": 35.01,
        "length_m": 2500,
        "lda_m": 2500,
        "lat_dms": "353256.47N",
        "lon_dms": "1394540.60E",
        "lat": 35.54901944,
        "lon": 139.76127778,
        "usable_for_landing": True,
    },
    ("RJTT", "22"): {
        "heading": 215.01,
        "length_m": 2500,
        "lda_m": 2500,
        "lat_dms": "353402.88N",
        "lon_dms": "1394637.61E",
        "lat": 35.56746667,
        "lon": 139.77711389,
        "usable_for_landing": True,
    },
    ("RJTT", "05"): {
        "heading": 42.56,
        "length_m": 2500,
        "lda_m": None,
        "lat_dms": "353126.41N",
        "lon_dms": "1394812.47E",
        "lat": 35.52400278,
        "lon": 139.80346389,
        "usable_for_landing": False,
    },
    ("RJTT", "23"): {
        "heading": 222.56,
        "length_m": 2500,
        "lda_m": 2500,
        "lat_dms": "353226.15N",
        "lon_dms": "1394919.61E",
        "lat": 35.54059722,
        "lon": 139.82211389,
        "usable_for_landing": True,
    },
    ("RJAA", "16R"): {
        "heading": 149.50,
        "length_m": 4000,
        "lda_m": 4000,
        "lat_dms": "354627.80N",
        "lon_dms": "1402205.85E",
        "lat": 35.77438889,
        "lon": 140.36829167,
        "usable_for_landing": True,
    },
    ("RJAA", "34L"): {
        "heading": 329.51,
        "length_m": 4000,
        "lda_m": 4000,
        "lat_dms": "354435.96N",
        "lon_dms": "1402326.66E",
        "lat": 35.74332222,
        "lon": 140.39073889,
        "usable_for_landing": True,
    },
    ("RJAA", "16L"): {
        "heading": 149.50,
        "length_m": 2500,
        "lda_m": 2500,
        "lat_dms": "354818.72N",
        "lon_dms": "1402241.19E",
        "lat": 35.80520000,
        "lon": 140.37810833,
        "usable_for_landing": True,
    },
    ("RJAA", "34R"): {
        "heading": 329.51,
        "length_m": 2500,
        "lda_m": 2500,
        "lat_dms": "354708.82N",
        "lon_dms": "1402331.72E",
        "lat": 35.78578333,
        "lon": 140.39214444,
        "usable_for_landing": True,
    },
}

OPPOSITE_PAIRS = [
    (("RJTT", "16R"), ("RJTT", "34L")),
    (("RJTT", "16L"), ("RJTT", "34R")),
    (("RJTT", "04"), ("RJTT", "22")),
    (("RJTT", "05"), ("RJTT", "23")),
    (("RJAA", "16R"), ("RJAA", "34L")),
    (("RJAA", "16L"), ("RJAA", "34R")),
]

PARALLEL_GROUPS = [
    [("RJTT", "16R"), ("RJTT", "16L")],
    [("RJTT", "34L"), ("RJTT", "34R")],
    [("RJAA", "16R"), ("RJAA", "16L")],
    [("RJAA", "34L"), ("RJAA", "34R")],
]


def dms_to_decimal(dms: str) -> float:
    hemi = dms[-1].upper()
    body = dms[:-1]
    if hemi in ("N", "S"):
        deg = int(body[:2])
        rem = body[2:]
    elif hemi in ("E", "W"):
        deg = int(body[:3])
        rem = body[3:]
    else:
        raise ValueError(f"Bad DMS: {dms}")
    minutes = int(rem[:2])
    seconds = float(rem[2:])
    value = deg + minutes / 60.0 + seconds / 3600.0
    if hemi in ("S", "W"):
        value = -value
    return value


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_KM * 1000.0 * math.asin(math.sqrt(a))


def heading_delta(a: float, b: float) -> float:
    d = (b - a) % 360.0
    if d > 180.0:
        d -= 360.0
    return d


def landing_or_physical(entry: dict, prefer_physical: bool) -> tuple[float, float]:
    if prefer_physical and "phys_lat_dms" in entry:
        return dms_to_decimal(entry["phys_lat_dms"]), dms_to_decimal(entry["phys_lon_dms"])
    return entry["lat"], entry["lon"]


def main() -> int:
    lines: list[str] = []
    failures: list[str] = []

    def log(msg: str) -> None:
        lines.append(msg)
        print(msg)

    log("=== Runway catalog cross-check (R=6378.1 km) ===")
    log(f"JSON: {RUNWAYS_JSON}")
    log(
        "Source note: unofficial AD 2.12 copies + AirBinder AIRAC 2611; "
        "NOT verified against official AIP in force."
    )

    with RUNWAYS_JSON.open(encoding="utf-8") as f:
        payload = json.load(f)

    meta = payload.get("catalogSource", {})
    log(f"catalogSource.verificationNote: {meta.get('verificationNote', '(missing)')}")

    by_key: dict[tuple[str, str], dict] = {}
    for rwy in payload["runways"]:
        by_key[(rwy["airportIcao"], rwy["designator"])] = rwy

    if len(by_key) != 12:
        failures.append(f"expected 12 surveyed ends, found {len(by_key)}")

    for key, table in SURVEY_TABLE.items():
        log(f"\n-- {key[0]} {key[1]} --")
        dms_lat = dms_to_decimal(table["lat_dms"])
        dms_lon = dms_to_decimal(table["lon_dms"])
        log(f"DMS→dec: {table['lat_dms']} {table['lon_dms']} → {dms_lat:.8f}, {dms_lon:.8f}")
        if abs(dms_lat - table["lat"]) > LATLON_TOL_DEG or abs(dms_lon - table["lon"]) > LATLON_TOL_DEG:
            failures.append(f"{key}: DMS decimal mismatch vs table")

        if key not in by_key:
            failures.append(f"{key}: missing from runways.json")
            continue
        json_rwy = by_key[key]
        for field, expected, tol in (
            ("thresholdLatitude", table["lat"], LATLON_TOL_DEG),
            ("thresholdLongitude", table["lon"], LATLON_TOL_DEG),
            ("heading", table["heading"], HEADING_TOL_DEG),
        ):
            actual = float(json_rwy[field])
            ok = abs(actual - expected) <= tol
            log(f"JSON {field}: {actual} (table {expected}) {'OK' if ok else 'FAIL'}")
            if not ok:
                failures.append(f"{key}.{field}: {actual} != {expected}")

        if json_rwy.get("lengthM") != table["length_m"]:
            failures.append(f"{key}.lengthM mismatch")
        if bool(json_rwy.get("usableForLanding", True)) != table["usable_for_landing"]:
            failures.append(f"{key}.usableForLanding mismatch")

        if "phys_lat_dms" in table:
            phys_lat = dms_to_decimal(table["phys_lat_dms"])
            phys_lon = dms_to_decimal(table["phys_lon_dms"])
            dist = haversine_m(phys_lat, phys_lon, table["lat"], table["lon"])
            expected_disp = table["displaced_m"]
            ok = abs(dist - expected_disp) <= DISPLACED_TOL_M
            log(f"displaced distance: {dist:.3f} m (expect {expected_disp} ±{DISPLACED_TOL_M}) {'OK' if ok else 'FAIL'}")
            if not ok:
                failures.append(f"{key}: displaced {dist:.3f} m")
            j_phys_lat = json_rwy.get("physicalThresholdLatitude")
            j_phys_lon = json_rwy.get("physicalThresholdLongitude")
            if j_phys_lat is None or j_phys_lon is None:
                failures.append(f"{key}: missing physicalThreshold*")
            elif abs(j_phys_lat - phys_lat) > LATLON_TOL_DEG or abs(j_phys_lon - phys_lon) > LATLON_TOL_DEG:
                failures.append(f"{key}: physicalThreshold mismatch")

    log("\n=== Opposite-end lengths / reciprocal headings / parallels ===")
    for a_key, b_key in OPPOSITE_PAIRS:
        a, b = SURVEY_TABLE[a_key], SURVEY_TABLE[b_key]
        # Pavement length: physical-to-physical when displaced, else landing-to-landing.
        a_phys = landing_or_physical(a, prefer_physical=True)
        b_phys = landing_or_physical(b, prefer_physical=True)
        pavement_m = haversine_m(a_phys[0], a_phys[1], b_phys[0], b_phys[1])
        expect_len = a["length_m"]
        ok_len = abs(pavement_m - expect_len) <= LENGTH_TOL_M
        log(
            f"{a_key[1]}↔{b_key[1]} pavement {pavement_m:.3f} m "
            f"(expect {expect_len} ±{LENGTH_TOL_M}) {'OK' if ok_len else 'FAIL'}"
        )
        if not ok_len:
            failures.append(f"{a_key}/{b_key} pavement length")

        if a.get("lda_m") is not None:
            lda_m = haversine_m(a["lat"], a["lon"], b_phys[0], b_phys[1])
            ok_lda = abs(lda_m - a["lda_m"]) <= LENGTH_TOL_M
            log(
                f"{a_key[1]} LDA-ish landing→opp phys {lda_m:.3f} m "
                f"(expect {a['lda_m']} ±{LENGTH_TOL_M}) {'OK' if ok_lda else 'FAIL'}"
            )
            if not ok_lda:
                failures.append(f"{a_key} LDA distance")

        delta = abs(heading_delta(a["heading"], b["heading"]))
        ok_recip = abs(delta - 180.0) <= RECIPROCAL_TOL_DEG
        log(
            f"{a_key[1]}/{b_key[1]} heading delta {delta:.4f}° "
            f"(expect ~180 ±{RECIPROCAL_TOL_DEG}) {'OK' if ok_recip else 'FAIL'}"
        )
        if not ok_recip:
            failures.append(f"{a_key}/{b_key} reciprocal heading")

    for group in PARALLEL_GROUPS:
        headings = [SURVEY_TABLE[k]["heading"] for k in group]
        spread = max(headings) - min(headings)
        ok = spread <= PARALLEL_TOL_DEG
        names = "/".join(k[1] for k in group)
        log(f"parallel {names}: headings {headings} spread {spread:.4f}° {'OK' if ok else 'FAIL'}")
        if not ok:
            failures.append(f"parallel {names}")

    # Surveyed 34L must not match old 16R (163° / 3360 m) derivation.
    old_16r_lat, old_16r_lon, old_hdg, old_len = 35.5530, 139.7810, 163.0, 3360.0
    ang = (old_len / 1000.0) / EARTH_RADIUS_KM
    brng = math.radians(old_hdg)
    lat1 = math.radians(old_16r_lat)
    lon1 = math.radians(old_16r_lon)
    lat2 = math.asin(
        math.sin(lat1) * math.cos(ang) + math.cos(lat1) * math.sin(ang) * math.cos(brng)
    )
    lon2 = lon1 + math.atan2(
        math.sin(brng) * math.sin(ang) * math.cos(lat1),
        math.cos(ang) - math.sin(lat1) * math.sin(lat2),
    )
    derived_lat, derived_lon = math.degrees(lat2), math.degrees(lon2)
    survey_34l = SURVEY_TABLE[("RJTT", "34L")]
    drift_m = haversine_m(derived_lat, derived_lon, survey_34l["lat"], survey_34l["lon"])
    log(f"\nOld 16R-derived 34L drift vs survey: {drift_m:.1f} m (must be >> 15 m)")
    if drift_m < 200:
        failures.append("surveyed 34L too close to old 16R derivation")

    log("\n=== Result ===")
    if failures:
        log(f"FAIL ({len(failures)}):")
        for f in failures:
            log(f"  - {f}")
        out = Path(__file__).with_name("crosscheck_output.txt")
        out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return 1

    log("PASS: all 12 ends cross-checked")
    out = Path(__file__).with_name("crosscheck_output.txt")
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    log(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
