package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

class AtisTextComposerTest {

    @Test
    void composeGoldenBothMissing() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'A', "16R", UniformWind.missing(), null);
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION ALPHA.
                LANDING AND DEPARTURE RUNWAY 16R.
                WIND AND WEATHER NOT AVAILABLE.
                ADVISE YOU HAVE INFORMATION ALPHA.""",
                text);
        assertFalse(text.contains("CALM"));
        assertFalse(text.matches("(?s).*\\bQNH\\b.*\\d+.*"));
        assertFalse(text.matches("(?s).*\\bWIND\\b.*\\d+.*"));
    }

    @Test
    void composeGoldenWindOnly() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport",
                "RJTT",
                'C',
                "22",
                UniformWind.measured(160, 12),
                null);
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION CHARLIE.
                LANDING AND DEPARTURE RUNWAY 22.
                WIND 160 DEGREES 12 KNOTS.
                WEATHER NOT AVAILABLE.
                ADVISE YOU HAVE INFORMATION CHARLIE.""",
                text);
        assertFalse(text.matches("(?s).*\\bQNH\\b.*\\d+.*"));
    }

    @Test
    void composeGoldenQnhOnly() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'B', "16R", UniformWind.missing(), 1013);
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION BRAVO.
                LANDING AND DEPARTURE RUNWAY 16R.
                WIND NOT AVAILABLE.
                QNH 1013.
                ADVISE YOU HAVE INFORMATION BRAVO.""",
                text);
    }

    @Test
    void composeGoldenWindAndQnh() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport",
                "RJTT",
                'D',
                "16R",
                UniformWind.measured(270, 15),
                995);
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION DELTA.
                LANDING AND DEPARTURE RUNWAY 16R.
                WIND 270 DEGREES 15 KNOTS.
                QNH 0995.
                ADVISE YOU HAVE INFORMATION DELTA.""",
                text);
        assertFalse(text.contains("WEATHER NOT AVAILABLE"));
    }

    @Test
    void composeGoldenCalmWithQnh() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'E', "16R", UniformWind.calm(), 1013);
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION ECHO.
                LANDING AND DEPARTURE RUNWAY 16R.
                WIND CALM.
                QNH 1013.
                ADVISE YOU HAVE INFORMATION ECHO.""",
                text);
    }

    @Test
    void composeGoldenCalmWithoutQnh() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'B', "16R", UniformWind.calm());
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION BRAVO.
                LANDING AND DEPARTURE RUNWAY 16R.
                WIND CALM.
                WEATHER NOT AVAILABLE.
                ADVISE YOU HAVE INFORMATION BRAVO.""",
                text);
    }

    @Test
    void nextIdentWrapsZuluToAlpha() {
        assertEquals('B', AtisTextComposer.nextIdent('A'));
        assertEquals('A', AtisTextComposer.nextIdent('Z'));
    }
}
