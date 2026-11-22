package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import java.util.Locale;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

/**
 * Builds ATIS text from runway, session wind, and optional per-airport QNH.
 * Missing observations are stated explicitly rather than fabricated.
 */
public final class AtisTextComposer {

    private static final String[] ICAO_PHONETIC = {
            "ALPHA", "BRAVO", "CHARLIE", "DELTA", "ECHO", "FOXTROT", "GOLF", "HOTEL",
            "INDIA", "JULIETT", "KILO", "LIMA", "MIKE", "NOVEMBER", "OSCAR", "PAPA",
            "QUEBEC", "ROMEO", "SIERRA", "TANGO", "UNIFORM", "VICTOR", "WHISKEY",
            "XRAY", "YANKEE", "ZULU"
    };

    private AtisTextComposer() {
    }

    public static String compose(
            String airportName,
            String airportIcao,
            char informationIdent,
            String runwayInUse,
            UniformWind wind) {
        return compose(airportName, airportIcao, informationIdent, runwayInUse, wind, null);
    }

    public static String compose(
            String airportName,
            String airportIcao,
            char informationIdent,
            String runwayInUse,
            UniformWind wind,
            Integer qnhHpa) {
        UniformWind sessionWind = wind != null ? wind : UniformWind.missing();
        String name = airportName == null || airportName.isBlank()
                ? airportIcao
                : airportName.trim();
        String icao = airportIcao.toUpperCase(Locale.ROOT).trim();
        String letterWord = phoneticWord(informationIdent);
        String runway = runwayInUse.toUpperCase(Locale.ROOT).trim();
        return name + " (" + icao + ") INFORMATION " + letterWord + ".\n"
                + "LANDING AND DEPARTURE RUNWAY " + runway + ".\n"
                + windAndWeatherSection(sessionWind, qnhHpa)
                + "ADVISE YOU HAVE INFORMATION " + letterWord + ".";
    }

    private static String windAndWeatherSection(UniformWind wind, Integer qnhHpa) {
        boolean hasWind = !wind.isMissing();
        boolean hasQnh = qnhHpa != null;
        if (!hasWind && !hasQnh) {
            return "WIND AND WEATHER NOT AVAILABLE.\n";
        }
        StringBuilder section = new StringBuilder();
        if (!hasWind) {
            section.append("WIND NOT AVAILABLE.\n");
        } else if (wind.isCalm()) {
            section.append("WIND CALM.\n");
        } else {
            section.append(String.format(
                    Locale.ROOT,
                    "WIND %03d DEGREES %d KNOTS.\n",
                    wind.directionDegOrZero(),
                    wind.speedKnotsOrZero()));
        }
        if (hasQnh) {
            // JMA METAR uses Q0995; ATIS prose keeps the QNH label with the same 4-digit integer.
            section.append(String.format(Locale.ROOT, "QNH %04d.\n", qnhHpa));
        } else {
            section.append("WEATHER NOT AVAILABLE.\n");
        }
        return section.toString();
    }

    public static String phoneticWord(char informationIdent) {
        char upper = Character.toUpperCase(informationIdent);
        if (upper < 'A' || upper > 'Z') {
            throw new IllegalArgumentException("informationIdent must be A-Z");
        }
        return ICAO_PHONETIC[upper - 'A'];
    }

    public static char nextIdent(char current) {
        char upper = Character.toUpperCase(current);
        if (upper < 'A' || upper > 'Z') {
            throw new IllegalArgumentException("informationIdent must be A-Z");
        }
        return upper == 'Z' ? 'A' : (char) (upper + 1);
    }
}
