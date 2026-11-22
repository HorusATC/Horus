package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import java.util.Locale;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

/**
 * Builds ATIS text without live weather values beyond session wind.
 * Missing wind observations are stated explicitly rather than fabricated.
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
        UniformWind sessionWind = wind != null ? wind : UniformWind.missing();
        String name = airportName == null || airportName.isBlank()
                ? airportIcao
                : airportName.trim();
        String icao = airportIcao.toUpperCase(Locale.ROOT).trim();
        String letterWord = phoneticWord(informationIdent);
        String runway = runwayInUse.toUpperCase(Locale.ROOT).trim();
        return name + " (" + icao + ") INFORMATION " + letterWord + ".\n"
                + "LANDING AND DEPARTURE RUNWAY " + runway + ".\n"
                + windSection(sessionWind)
                + "ADVISE YOU HAVE INFORMATION " + letterWord + ".";
    }

    private static String windSection(UniformWind wind) {
        if (wind.isMissing()) {
            return "WIND AND WEATHER NOT AVAILABLE.\n";
        }
        if (wind.isCalm()) {
            return "WIND CALM.\nWEATHER NOT AVAILABLE.\n";
        }
        int direction = wind.directionDegOrZero();
        int speed = wind.speedKnotsOrZero();
        return String.format(
                Locale.ROOT,
                "WIND %03d DEGREES %d KNOTS.\nWEATHER NOT AVAILABLE.\n",
                direction,
                speed);
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
