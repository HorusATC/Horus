package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Contract test that mechanically parses source files to extract API endpoints
 * and verifies they exist in Spring request mappings.
 * Prevents issues like PR #119 (wrong path/method).
 */
@SpringBootTest
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final Path FRONTEND_API_DIR =
            Paths.get("../Frontend/app/api").toAbsolutePath().normalize();
    private static final Path BACKEND_TEST_DIR =
            Paths.get("src/test/java").toAbsolutePath().normalize();

    /** Expected raw IntegrationTest call sites (hold validation + location holding.pattern + ATIS). */
    private static final int EXPECTED_BACKEND_RAW_CALLS = 58;

    /** Expected unique keys after normalizing callsigns/airport ICAO to Spring path variables. */
    private static final int EXPECTED_BACKEND_NORMALIZED_UNIQUE_KEYS = 18;

    @Test
    void allFrontendBffCallsShouldMatchActualSpringMappings() throws IOException {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        Map<String, List<String>> callsByFile = extractFrontendBffCallsByFile();

        int totalRouteFiles = callsByFile.size();
        int totalExportedHandlers = countExportedHandlers();

        assertThat(totalRouteFiles)
                .as("Frontend BFF extraction should find ≥25 route.ts files (found " + totalRouteFiles + ")")
                .isGreaterThanOrEqualTo(25);

        Map<String, String> frontendCalls = new LinkedHashMap<>();
        List<String> filesWithZeroCalls = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : callsByFile.entrySet()) {
            String file = entry.getKey();
            List<String> calls = entry.getValue();

            if (calls.isEmpty()) {
                filesWithZeroCalls.add(file);
            }

            for (String call : calls) {
                frontendCalls.put(call, file);
            }
        }

        if (!filesWithZeroCalls.isEmpty()) {
            fail("Frontend BFF route.ts files with 0 extracted backend calls:\n"
                    + String.join("\n", filesWithZeroCalls)
                    + "\n\nEvery route.ts must call proxyToBackend with a string/template literal at the call site.");
        }

        assertThat(frontendCalls)
                .as("Frontend BFF extraction should find ≥25 endpoints (found " + frontendCalls.size() + ")")
                .hasSizeGreaterThanOrEqualTo(25);

        assertThat(frontendCalls.size())
                .as("Number of extracted backend calls (" + frontendCalls.size()
                        + ") should match exported handlers (" + totalExportedHandlers + ")")
                .isEqualTo(totalExportedHandlers);

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : frontendCalls.entrySet()) {
            String endpoint = entry.getKey();
            String sourceFile = entry.getValue();

            if (!matchesAnyMapping(endpoint, actualMappings)) {
                missingEndpoints.add(String.format("%s (from %s)", endpoint, sourceFile));
            }
        }

        if (!missingEndpoints.isEmpty()) {
            fail("Frontend BFF routes call non-existent backend endpoints:\n"
                    + String.join("\n", missingEndpoints));
        }
    }

    @Test
    void allBackendTestCallsShouldMatchActualSpringMappings() throws IOException {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        List<RawBackendCall> rawCalls = extractBackendTestCallsRaw();
        int integrationTestFileCount = countIntegrationTestFiles();

        assertThat(integrationTestFileCount)
                .as("Should find ≥5 IntegrationTest.java files (found " + integrationTestFileCount + ")")
                .isGreaterThanOrEqualTo(5);

        assertThat(rawCalls)
                .as("Backend test extraction should find exactly " + EXPECTED_BACKEND_RAW_CALLS
                        + " raw call sites (found " + rawCalls.size() + ")")
                .hasSize(EXPECTED_BACKEND_RAW_CALLS);

        Map<String, String> normalizedUnique = new LinkedHashMap<>();
        List<String> missingEndpoints = new ArrayList<>();

        for (RawBackendCall call : rawCalls) {
            String rawEndpoint = call.method() + " " + call.path();
            String normalized = normalizeToSpringMappingKey(rawEndpoint, actualMappings);
            if (normalized == null) {
                missingEndpoints.add(String.format("%s (from %s)", rawEndpoint, call.sourceFile()));
            } else {
                normalizedUnique.put(normalized, call.sourceFile());
            }
        }

        if (!missingEndpoints.isEmpty()) {
            fail("Backend integration tests call non-existent endpoints:\n"
                    + String.join("\n", missingEndpoints));
        }

        assertThat(normalizedUnique)
                .as("Backend test paths should normalize to exactly "
                        + EXPECTED_BACKEND_NORMALIZED_UNIQUE_KEYS
                        + " unique Spring mapping keys (found " + normalizedUnique.size()
                        + ": " + normalizedUnique.keySet() + ")")
                .hasSize(EXPECTED_BACKEND_NORMALIZED_UNIQUE_KEYS);
    }

    private Map<String, List<String>> extractFrontendBffCallsByFile() throws IOException {
        Map<String, List<String>> callsByFile = new LinkedHashMap<>();

        if (!Files.exists(FRONTEND_API_DIR)) {
            return callsByFile;
        }

        try (Stream<Path> paths = Files.walk(FRONTEND_API_DIR)) {
            List<Path> routeFiles =
                    paths.filter(p -> p.getFileName().toString().equals("route.ts"))
                            .collect(Collectors.toList());

            for (Path routeFile : routeFiles) {
                String content = Files.readString(routeFile);
                String relPath = FRONTEND_API_DIR.relativize(routeFile).toString();
                List<String> fileCalls = new ArrayList<>();

                Pattern functionPattern = Pattern.compile(
                        "export\\s+async\\s+function\\s+(GET|POST|PUT|DELETE|PATCH)\\s*\\(",
                        Pattern.MULTILINE);
                Matcher functionMatcher = functionPattern.matcher(content);

                while (functionMatcher.find()) {
                    String method = functionMatcher.group(1);
                    int start = functionMatcher.start();
                    int end = Math.min(start + 1000, content.length());
                    String functionBody = content.substring(start, end);

                    Pattern proxyPattern = Pattern.compile(
                            "proxyToBackend\\s*\\(\\s*([`\"])([^`\"]+?)\\1");
                    Matcher proxyMatcher = proxyPattern.matcher(functionBody);

                    while (proxyMatcher.find()) {
                        String path = proxyMatcher.group(2);
                        path = normalizeFrontendPath(path);
                        fileCalls.add(method + " " + path);
                    }

                    Pattern fetchPattern = Pattern.compile(
                            "fetch\\s*\\(\\s*`([^`]+)`");
                    Matcher fetchMatcher = fetchPattern.matcher(functionBody);

                    while (fetchMatcher.find()) {
                        String path = fetchMatcher.group(1);
                        path = normalizeFrontendPath(path);
                        fileCalls.add(method + " " + path);
                    }
                }

                callsByFile.put(relPath, fileCalls);
            }
        }

        return callsByFile;
    }

    private int countExportedHandlers() throws IOException {
        if (!Files.exists(FRONTEND_API_DIR)) {
            return 0;
        }

        int count = 0;
        try (Stream<Path> paths = Files.walk(FRONTEND_API_DIR)) {
            List<Path> routeFiles =
                    paths.filter(p -> p.getFileName().toString().equals("route.ts"))
                            .collect(Collectors.toList());

            Pattern functionPattern = Pattern.compile(
                    "export\\s+async\\s+function\\s+(GET|POST|PUT|DELETE|PATCH)\\s*\\(",
                    Pattern.MULTILINE);

            for (Path routeFile : routeFiles) {
                String content = Files.readString(routeFile);
                Matcher matcher = functionPattern.matcher(content);
                while (matcher.find()) {
                    count++;
                }
            }
        }

        return count;
    }

    private String normalizeFrontendPath(String path) {
        path = path.replaceAll("\\?.*$", "");
        path = path.replaceAll("\\$\\{encodeURIComponent\\([^)]+\\)\\}", "{callsign}");
        path = path.replaceAll("\\$\\{callsign\\}", "{callsign}");
        path = path.replaceAll("\\$\\{action\\}", "{action}");
        path = path.replaceAll("\\$\\{[^}]+\\}", "{param}");
        return path;
    }

    /**
     * Every IntegrationTest {@code baseUrl() + "..."} call site, without collapsing
     * duplicate method+path keys. Callsign literals stay as written until
     * {@link #normalizeToSpringMappingKey} maps them onto Spring patterns.
     */
    private List<RawBackendCall> extractBackendTestCallsRaw() throws IOException {
        List<RawBackendCall> calls = new ArrayList<>();

        if (!Files.exists(BACKEND_TEST_DIR)) {
            return calls;
        }

        try (Stream<Path> paths = Files.walk(BACKEND_TEST_DIR)) {
            List<Path> testFiles = paths
                    .filter(p -> p.toString().endsWith("IntegrationTest.java"))
                    .collect(Collectors.toList());

            for (Path testFile : testFiles) {
                String content = Files.readString(testFile);
                String fileName = testFile.getFileName().toString();

                Pattern pathPattern = Pattern.compile("baseUrl\\(\\)\\s*\\+\\s*[\"']([^\"']+)[\"']");
                Matcher pathMatcher = pathPattern.matcher(content);

                while (pathMatcher.find()) {
                    String path = pathMatcher.group(1);
                    int pos = pathMatcher.start();
                    String before = content.substring(Math.max(0, pos - 150), pos);

                    String method = "";
                    if (before.contains("postForEntity")) method = "POST";
                    else if (before.contains("getForEntity")) method = "GET";
                    else if (before.contains("putForObject")
                            || before.contains("HttpMethod.PUT")) method = "PUT";
                    else if (before.contains("delete(")) method = "DELETE";
                    else if (before.contains("patchForEntity")) method = "PATCH";

                    if (!method.isEmpty() && path.startsWith("/")) {
                        path = path.replaceAll("\\?.*$", "");
                        calls.add(new RawBackendCall(method, path, fileName));
                    }
                }
            }
        }

        return calls;
    }

    private int countIntegrationTestFiles() throws IOException {
        if (!Files.exists(BACKEND_TEST_DIR)) {
            return 0;
        }

        try (Stream<Path> paths = Files.walk(BACKEND_TEST_DIR)) {
            return (int) paths.filter(p -> p.toString().endsWith("IntegrationTest.java")).count();
        }
    }

    /**
     * Map a concrete test endpoint onto the Spring mapping key it matches
     * (e.g. {@code POST /api/aircraft/JAL123/squawk} → {@code POST /api/aircraft/{callsign}/squawk}).
     * Returns null when no mapping matches so the test fails on wrong paths.
     */
    private String normalizeToSpringMappingKey(
            String endpoint, Map<String, RequestMappingInfo> mappings) {
        if (mappings.containsKey(endpoint)) {
            return endpoint;
        }

        String[] parts = endpoint.split(" ", 2);
        if (parts.length != 2) {
            return null;
        }
        String method = parts[0];
        String path = parts[1];

        for (String mappingKey : mappings.keySet()) {
            String[] mappingParts = mappingKey.split(" ", 2);
            if (mappingParts.length != 2) continue;

            String mappingMethod = mappingParts[0];
            String mappingPath = mappingParts[1];

            if (method.equals(mappingMethod) && pathsMatch(path, mappingPath)) {
                return mappingMethod + " " + mappingPath;
            }
        }

        return null;
    }

    private boolean matchesAnyMapping(String endpoint, Map<String, RequestMappingInfo> mappings) {
        return normalizeToSpringMappingKey(endpoint, mappings) != null;
    }

    private boolean pathsMatch(String requestPath, String mappingPath) {
        String requestPattern = requestPath.replaceAll("\\{[^}]+\\}", "([^/]+)");
        String mappingPattern = mappingPath.replaceAll("\\{[^}]+\\}", "([^/]+)");

        if (requestPattern.equals(mappingPattern)) {
            return true;
        }

        Pattern reqRegex = Pattern.compile("^" + requestPattern + "$");
        Pattern mapRegex = Pattern.compile("^" + mappingPattern + "$");

        return reqRegex.matcher(mappingPath).matches() || mapRegex.matcher(requestPath).matches();
    }

    private Map<String, RequestMappingInfo> getActualSpringMappings() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods =
                requestMappingHandlerMapping.getHandlerMethods();

        Map<String, RequestMappingInfo> result = new HashMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();

            Set<String> patterns = info.getPatternValues();
            Set<String> methods = info.getMethodsCondition().getMethods().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet());

            if (methods.isEmpty()) {
                for (String pattern : patterns) {
                    result.put("ALL " + pattern, info);
                }
            } else {
                for (String method : methods) {
                    for (String pattern : patterns) {
                        result.put(method + " " + pattern, info);
                    }
                }
            }
        }

        return result;
    }

    private record RawBackendCall(String method, String path, String sourceFile) {}
}
