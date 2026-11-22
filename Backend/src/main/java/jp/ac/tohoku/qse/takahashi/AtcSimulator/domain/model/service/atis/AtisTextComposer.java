package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import java.util.Locale;

/**
 * Builds ATIS text without wind or live weather values.
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
            String runwayInUse) {
        String name = airportName == null || airportName.isBlank()
                ? airportIcao
                : airportName.trim();
        String icao = airportIcao.toUpperCase(Locale.ROOT).trim();
        String letterWord = phoneticWord(informationIdent);
        String runway = runwayInUse.toUpperCase(Locale.ROOT).trim();
        return name + " (" + icao + ") INFORMATION " + letterWord + ".\n"
                + "LANDING AND DEPARTURE RUNWAY " + runway + ".\n"
                + "WIND AND WEATHER NOT AVAILABLE.\n"
                + "ADVISE YOU HAVE INFORMATION " + letterWord + ".";
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
