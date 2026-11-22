package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Bit-identical valid-command traces vs the golden recorded on main {@code 4ce741f}.
 */
class ValidCommandTraceGoldenTest {

    private static final String GOLDEN_RESOURCE =
            "/nav-mode-validate/valid-command-trace-4ce741f.csv";

    @Test
    void validCommandScriptMatchesMainGolden() throws IOException {
        String actual = ValidCommandTraceRecorder.record();
        if (Boolean.parseBoolean(System.getenv().getOrDefault("WRITE_NAV_MODE_GOLDEN", "false"))) {
            Path out = Path.of("src/test/resources" + GOLDEN_RESOURCE);
            Files.createDirectories(out.getParent());
            Files.writeString(out, actual, StandardCharsets.UTF_8);
            return;
        }
        String expected;
        try (var in = ValidCommandTraceGoldenTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            if (in == null) {
                throw new AssertionError("Missing golden resource " + GOLDEN_RESOURCE);
            }
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual);
    }
}
