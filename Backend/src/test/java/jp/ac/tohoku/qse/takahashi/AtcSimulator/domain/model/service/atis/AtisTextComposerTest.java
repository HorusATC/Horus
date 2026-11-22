package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.atis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AtisTextComposerTest {

    @Test
    void composeGoldenRjttAlpha16R() {
        String text = AtisTextComposer.compose(
                "Tokyo International Airport", "RJTT", 'A', "16R");
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
    void nextIdentWrapsZuluToAlpha() {
        assertEquals('B', AtisTextComposer.nextIdent('A'));
        assertEquals('A', AtisTextComposer.nextIdent('Z'));
    }
}
