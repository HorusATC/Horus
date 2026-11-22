package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

class AtisTextComposerTest {

    @Test
    void composeGoldenRjttAlpha16R() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'A', "16R", UniformWind.missing());
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
    void composeGoldenCalm() {
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
    void composeGoldenMeasuredWind() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'C', "22", UniformWind.measured(160, 12));
        assertEquals(
                """
                Tokyo International Airport (RJTT) INFORMATION CHARLIE.
                LANDING AND DEPARTURE RUNWAY 22.
                WIND 160 DEGREES 12 KNOTS.
                WEATHER NOT AVAILABLE.
                ADVISE YOU HAVE INFORMATION CHARLIE.""",
                text);
    }

    @Test
    void nextIdentWrapsZuluToAlpha() {
        assertEquals('B', AtisTextComposer.nextIdent('A'));
        assertEquals('A', AtisTextComposer.nextIdent('Z'));
    }
}
