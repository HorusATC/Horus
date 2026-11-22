package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Grep-style static check: production corridor selection must go through
 * {@code selectClosestCenterlineRunway} only.
 */
class SelectClosestCenterlineRunwayStaticTest {

    @Test
    void msawDetectorDelegatesCorridorSelection() throws IOException {
        Path root = resolveRepoRoot();
        String detector = Files.readString(
                root.resolve(
                        "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawDetector.java"),
                StandardCharsets.UTF_8);
        int start = detector.indexOf("private ApproachCorridorCheck checkApproachCorridor");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int end = detector.indexOf("\n    private MsawAlert checkApproachFloor", start);
        assertThat(end).isGreaterThan(start);
        String body = detector.substring(start, end);
        assertThat(body).contains("selectClosestCenterlineRunway");
        assertThat(body).doesNotContain("for (");
    }

    @Test
    void nameOnlyAppearsInAllowedFiles() throws IOException {
        Path root = resolveRepoRoot();
        List<String> hits = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString();
                        return !s.contains("/node_modules/")
                                && !s.contains("/.git/")
                                && !s.contains("/build/")
                                && !s.contains("/.next/")
                                && (s.endsWith(".java")
                                        || s.endsWith(".ts")
                                        || s.endsWith(".tsx")
                                        || s.endsWith(".md"));
                    })
                    .forEach(p -> {
                        try {
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            if (text.contains("selectClosestCenterlineRunway")) {
                                hits.add(root.relativize(p).toString().replace('\\', '/'));
                            }
                        } catch (IOException ignored) {
                            // skip unreadable
                        }
                    });
        }

        List<String> allowed = List.of(
                "Frontend/utility/measure/rangeBearing.ts",
                "Frontend/utility/measure/geometry.repro.test.ts",
                "Frontend/utility/measure/selectClosestCenterlineRunway.static.test.ts",
                "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/ClosestCenterlineRunway.java",
                "Backend/src/main/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawDetector.java",
                "Backend/src/test/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/MsawOverlapReproTest.java",
                "Backend/src/test/java/jp/ac/tohoku/qse/takahashi/AtcSimulator/domain/model/service/msaw/SelectClosestCenterlineRunwayStaticTest.java",
                "spec/20261123-frontend-polling-runway/spec.md",
                "spec/20261122-runway-distance-rbl/spec.md",
                "Frontend/README.md",
                "Backend/README.md");

        assertThat(hits).isNotEmpty();
        for (String hit : hits) {
            assertThat(allowed).as("unexpected hit %s", hit).contains(hit);
        }
    }

    private static Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.isDirectory(cwd.resolve("Backend")) && Files.isDirectory(cwd.resolve("Frontend"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("Backend"))
                && Files.isDirectory(parent.resolve("Frontend"))) {
            return parent;
        }
        throw new IllegalStateException("Cannot resolve repo root from " + cwd);
    }
}
