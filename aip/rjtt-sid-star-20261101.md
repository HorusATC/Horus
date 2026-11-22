# RJTT SID / STAR extract (AIRAC 1 Nov 2026)

Derived from AIP Japan (AIRAC 1 Nov 2026), may not be current, edited by the Horus project and not government-made.

Machine-readable companion: [`rjtt-sid-star-20261101.json`](./rjtt-sid-star-20261101.json).

Official chart PDFs (SWIM AIP viewer, in-force issue labelled AIRAC 1 Nov 2026, base `https://web.swim.mlit.go.jp/f2eapr/AIP/html/20261029/eAIP/20261101/`):

| AIP plate | File | Procedures | Page footer |
|---|---|---|---|
| RJTT AD 2.24.26 | `20261101/JP-AD-2.24.26-RJTT-en-JP.pdf` | OSHIMA 1A / 1K / 2C | EFF: 31 OCT 2024 |
| RJTT AD 2.24.27 | `20261101/JP-AD-2.24.27-RJTT-en-JP.pdf` | AKSEL 1A / 1K / 2C | EFF: 31 OCT 2024 |
| RJTT AD 2.24.53 | `20261101/JP-AD-2.24.53-RJTT-en-JP.pdf` | AROSA 1A / 1K / 2C | EFF: 31 OCT 2024 |
| RJTT AD 2.24.32 | `20261101/JP-AD-2.24.32-RJTT-en-JP.pdf` | POLIX 2A / 2K / 1C | EFF: 31 OCT 2024 |
| RJTT AD 2.24.71 | `20261101/JP-AD-2.24.71-RJTT-en-JP.pdf` | GODIN 2A / 2K / 1C | EFF: 31 OCT 2024 |
| RJTT AD 2.24.12 | `20261101/JP-AD-2.24.12-RJTT-en-JP.pdf` | VAMOS FOUR RNAV SID + TATEYAMA / DRAKY transitions | EFF: 31 OCT 2024 |
| RJTT AD 2.24.88 | `20261101/JP-AD-2.24.88-RJTT-en-JP.pdf` | VISIP ONE conventional SID | EFF: 2 OCT 2025 |

Footer dates are those printed on the plate. The files were taken from the 1 Nov 2026 issue; a plate that was not reissued keeps its older footer (listed as uncertain).

## Counts

- **15 RNAV STAR variants** (5 families × 3). Chart header on every coding page: `RNAV STAR RWY34R/34L`. **151** path legs (IF/TF). Published holds are attached to each variant (catalog only).
- **VAMOS FOUR** RNAV SID: **6** runway variants (16R, 16L, 34L/34R, 04, 05, 22) and **2** enroute transitions (TATEYAMA, DRAKY). Path descriptors used: VA, DF, IF, TF.
- **VISIP ONE** conventional SID: **3** runway groups. No coding table and no VISIP coordinate table.
- **53** waypoints with coordinates taken from the published waypoint tables (DMS kept as printed; decimal degrees derived as `DD + MM/60 + SS.s/3600`). No coordinate was invented.
- **VISIP** itself is shown only as `D28.1 TTE`. Its latitude/longitude is **missing** and is not estimated. TTE (VOR/DME 117.4) appears on that chart; its DMS was decoded from a symbol-font line and is marked uncertain.

## Notation (as printed)

- Path descriptors: IF, TF, VA, DF, Hold.
- Course: magnetic, and true in parentheses. Magnetic variation on these tables is **-7.9**. This extract stores both courses; it does not apply variation.
- Altitude: bare number = AT (matches narrative “at”); `+` = at or above; `-` = at or below; `FL150` is stored as a flight level, not converted to feet in the JSON.
- Path-table speeds 230 / 210 KIAS are unsigned. Hold-table speeds use a minus prefix (at or below 230 below 14000 ft, 240 above 14001 ft). The unsigned path-table restriction type is **uncertain** and is not inferred.
- Fly-over is `Y` only on VAMOS FOUR RWY05 leg 002 (TT501). All other extracted path legs are not fly-over (`–`).

## STAR variants (path waypoints)

| Procedure | Legs | End fix | Constrained waypoints |
|---|---:|---|---|
| OSHIMA 1A | 9 | ARLON | ANZAC AT 13000 / 230; WANDA AT 13000 / 230; WEDGE AT 8000 |
| OSHIMA 1K | 10 | KAIHO | same through WEDGE; UMUKI AT OR ABOVE 6000 |
| OSHIMA 2C | 10 | CREAM | CIVIC AT 7000 / 210; EPSON AT 7000 / 210 |
| AKSEL 1A | 7 | ARLON | AKSEL AT 12000 / 230; WALLY AT 12000 / 230; WEDGE AT 8000 |
| AKSEL 1K | 8 | KAIHO | same through WEDGE; UMUKI AT OR ABOVE 6000 |
| AKSEL 2C | 10 | CREAM | CIVIC AT 7000 / 210; EPSON AT 7000 / 210 |
| AROSA 1A | 8 | ARLON | AVEEY AT 11000 / 230; WALTZ AT 11000 / 230; WEDGE AT 8000 |
| AROSA 1K | 9 | KAIHO | same through WEDGE; UMUKI AT OR ABOVE 6000 |
| AROSA 2C | 10 | CREAM | AVEEY AT 11000 / 230; CIVIC AT 7000 / 210; EPSON AT 7000 / 210 |
| POLIX 2A | 12 | ARLON | POLIX AT FL150; CHIPS AT OR BELOW 13000; COLOR AT OR BELOW 11000; COACH AT 8000 / 210; EDDIE AT 8000 / 210 |
| POLIX 2K | 13 | KAIHO | same through ANDEN; UMUKI AT OR ABOVE 6000 |
| POLIX 1C | 10 | CREAM | same through EDDIE, then CREAM |
| GODIN 2A | 12 | ARLON | CHIPS AT OR BELOW 13000; COLOR AT OR BELOW 11000; COACH AT 8000 / 210; EDDIE AT 8000 / 210 |
| GODIN 2K | 13 | KAIHO | same through ANDEN; UMUKI AT OR ABOVE 6000 |
| GODIN 1C | 10 | CREAM | same through EDDIE, then CREAM |

A / K / C suffixes consistently end at ARLON, KAIHO (via UMUKI), and CREAM. That pattern is an observation from the tables; the charts do not assign one suffix to 34L or 34R alone.

## VAMOS FOUR (coding-table summary)

| Variant | Runways | Path |
|---|---|---|
| VAMOS4.RWY16R | 16R | VA 158°M (150.0°T) +500; DF T6R11; TF VAMOS 207 (199.5) 14.5 NM +9000 |
| VAMOS4.RWY16L | 16L | VA 158 (150.0) +500; DF T6L21; TF VAMOS 209 (200.7) 15.4 NM +9000 |
| VAMOS4.RWY34L_34R | 34L, 34R | VA 338 (330.0) +700; DF TT502 turn right; TF LOCUP 199 (190.9) 5.2 NM +5000; TF VAMOS 217 (209.5) 17.3 NM +9000 |
| VAMOS4.RWY04 | 04 | VA 043 (034.9) +700; then the same TT502 / LOCUP / VAMOS legs as 34L/34R |
| VAMOS4.RWY05 | 05 | VA 050 (042.4) +500; DF TT501 fly-over; DF TT502 turn right; then LOCUP / VAMOS as above |
| VAMOS4.RWY22 | 22 | VA 223 (214.9) +600; DF HOBBS turn left; TF BASSA 188 (179.9) 5.8 NM; TF VAMOS 188 (179.9) 8.9 NM +9000 |
| TATEYAMA TRANSITION | after VAMOS | IF VAMOS +9000; TF UTIBO 165 (157.0) 16.8 NM |
| DRAKY TRANSITION | after VAMOS | IF VAMOS +9000; TF DRAKY 218 (210.2) 22.2 NM; TF XAC 218 (210.1) 11.9 NM |

Published climb-gradient notes (not a path terminator): 5.0% up to 700 FT on 34L/34R/04, up to 500 FT on 05, up to 600 FT on 22. Chart notes also require DME/DME/IRU or GNSS, and RADAR service.

## VISIP ONE

Conventional SID. Three runway groups (04/34R/34L; 16R/16L; 05). Narrative uses runway heading, HDG 100 / 208 / 223, TTE DME, and TTE radials R178 / R120, with crossing altitudes at TTE R120 and VISIP. Full wording is in the JSON and is marked uncertain because of PDF text encoding. No legs with coordinates are provided.

## Cross-checks

- Every published DMS string was read from the coordinate tables a second time. The same identifier has the same DMS on every plate that lists it (no conflicts).
- For every TF/connected pair with both a published distance and coordinates, a spherical great-circle check (radius 6371 km, 1 NM = 1852 m) differs by at most **0.05 NM** and the initial true course differs by at most **0.05°**. No pair was flagged.
- Conversion formula (documented, not an AIP claim): degrees = `DD + MM/60 + SS.s/3600`, south/west negative.

## Horus `waypoints.json` (informational)

AIP DMS remains the extract source of truth. Compared with main `8e5b680`:

- Already present: 29 of 53 names. Largest absolute latitude or longitude difference among those is about **1.4×10⁻⁵ degrees** (do not overwrite the existing catalog).
- Missing from the catalog and required to fly these RNAV procedures: `XAC`, `T6R11`, `T6L21`, `TT450`–`TT468`, `TT501`, `TT502` (24 names).

`targetSpeed` on a Horus flight-plan waypoint is ground speed. AIP path speeds are KIAS and must not be copied there.

## Uncertain items

1. Path-table speed restriction type (unsigned 230 / 210 KIAS).
2. Mapping of STAR suffix A / K / C to a single runway (header is 34R/34L for all).
3. Page-footer effective dates versus the package AIRAC date (see above).
4. VISIP ONE narrative and TTE coordinates (non-standard PDF encoding); VISIP lat/lon missing.
5. VISIP HDG / radial reference frame (recorded as printed, not converted to true).

Nothing else in the RNAV coding or coordinate tables was filled in by guesswork.
