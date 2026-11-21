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
 * Contract test that verifies all API endpoints used by Frontend BFF routes
 * and Backend integration tests actually exist in Spring request mappings.
 * <p>
 * This test mechanically extracts URLs and methods from source code
 * (Frontend/app/api/&#42;&#42;/route.ts and Backend test files) and validates them
 * against actual Spring mappings. It prevents issues like PR #119 where tests
 * called non-existent endpoints.
 */
@SpringBootTest
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final Path FRONTEND_API_DIR =
            Paths.get("../Frontend/app/api").toAbsolutePath().normalize();
    private static final Path BACKEND_TEST_DIR =
            Paths.get("src/test/java").toAbsolutePath().normalize();

    @Test
    void allFrontendBffCallsShouldMatchActualSpringMappings() throws IOException {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        Map<String, String> frontendCalls = extractFrontendBffCalls();

        System.out.println("Frontend BFF calls extracted: " + frontendCalls.size());
        for (String key : frontendCalls.keySet()) {
            System.out.println("  " + key);
        }

        assertThat(frontendCalls)
                .as("Frontend BFF extraction should find endpoints (found " + frontendCalls.size() + ")")
                .hasSizeGreaterThan(20);

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : frontendCalls.entrySet()) {
            String endpoint = entry.getKey();
            String sourceFile = entry.getValue();

            if (!actualMappings.containsKey(endpoint)) {
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
        Map<String, String> backendTestCalls = extractBackendTestCalls();

        assertThat(backendTestCalls)
                .as("Backend test extraction should find endpoints")
                .hasSizeGreaterThan(5);

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : backendTestCalls.entrySet()) {
            String endpoint = entry.getKey();
            String sourceFile = entry.getValue();

            if (!actualMappings.containsKey(endpoint)) {
                missingEndpoints.add(String.format("%s (from %s)", endpoint, sourceFile));
            }
        }

        if (!missingEndpoints.isEmpty()) {
            fail("Backend integration tests call non-existent endpoints:\n"
                    + String.join("\n", missingEndpoints));
        }
    }

    private Map<String, String> extractFrontendBffCalls() throws IOException {
        Map<String, String> calls = new LinkedHashMap<>();

        if (!Files.exists(FRONTEND_API_DIR)) {
            return calls;
        }

        try (Stream<Path> paths = Files.walk(FRONTEND_API_DIR)) {
            List<Path> routeFiles =
                    paths.filter(p -> p.getFileName().toString().equals("route.ts"))
                            .collect(Collectors.toList());

            for (Path routeFile : routeFiles) {
                String content = Files.readString(routeFile);
                String relPath = FRONTEND_API_DIR.relativize(routeFile).toString();

                extractFromProxyToBackend(content, relPath, calls);
                extractFromFetch(content, relPath, calls);
            }
        }

        return calls;
    }

    private void extractFromProxyToBackend(String content, String sourceFile, Map<String, String> calls) {
        Pattern callPattern = Pattern.compile(
                "return\\s+proxyToBackend\\s*\\(\\s*[`\"']([^`\"']+)[`\"']\\s*,\\s*\\{[^}]*?method:\\s*[\"']([A-Z]+)[\"']",
                Pattern.DOTALL);
        Matcher matcher = callPattern.matcher(content);

        while (matcher.find()) {
            String path = matcher.group(1);
            String method = matcher.group(2);
            path = path.replaceAll("\\$\\{encodeURIComponent\\([^)]+\\)\\}", "{callsign}");
            path = path.replaceAll("\\$\\{[^}]+\\}", "{callsign}");
            String endpoint = method + " " + path;
            calls.put(endpoint, sourceFile);
        }
    }

    private void extractFromFetch(String content, String sourceFile, Map<String, String> calls) {
        Pattern fetchPattern = Pattern.compile(
                "fetch\\s*\\(\\s*`([^`]+)`[^)]*?method:\\s*[\"']([A-Z]+)[\"']",
                Pattern.DOTALL);
        Matcher matcher = fetchPattern.matcher(content);

        while (matcher.find()) {
            String path = matcher.group(1);
            String method = matcher.group(2);
            path = path.replaceAll("\\$\\{[^}]+\\}", "{callsign}");
            String endpoint = method + " " + path;
            calls.put(endpoint, sourceFile);
        }
    }

    private Map<String, String> extractBackendTestCalls() throws IOException {
        Map<String, String> calls = new LinkedHashMap<>();

        if (!Files.exists(BACKEND_TEST_DIR)) {
            return calls;
        }

        try (Stream<Path> paths = Files.walk(BACKEND_TEST_DIR)) {
            List<Path> testFiles = paths.filter(p -> p.toString().endsWith("Test.java"))
                    .collect(Collectors.toList());

            for (Path testFile : testFiles) {
                String content = Files.readString(testFile);
                String fileName = testFile.getFileName().toString();

                extractRestClientCalls(content, fileName, calls);
            }
        }

        return calls;
    }

    private void extractRestClientCalls(String content, String sourceFile, Map<String, String> calls) {
        Pattern getPattern = Pattern.compile("\\.get\\(\\)\\s*\\.uri\\([\"']([^\"']+)[\"']\\)");
        Pattern postPattern = Pattern.compile("\\.post\\(\\)\\s*\\.uri\\([\"']([^\"']+)[\"']\\)");
        Pattern putPattern = Pattern.compile("\\.put\\(\\)\\s*\\.uri\\([\"']([^\"']+)[\"']\\)");

        Matcher getMatcher = getPattern.matcher(content);
        while (getMatcher.find()) {
            String path = getMatcher.group(1);
            calls.put("GET " + path, sourceFile);
        }

        Matcher postMatcher = postPattern.matcher(content);
        while (postMatcher.find()) {
            String path = postMatcher.group(1);
            calls.put("POST " + path, sourceFile);
        }

        Matcher putMatcher = putPattern.matcher(content);
        while (putMatcher.find()) {
            String path = putMatcher.group(1);
            calls.put("PUT " + path, sourceFile);
        }
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
                        String normalized = pattern.replaceAll("\\{[^}]+\\}", "{dynamic}");
                        if (!normalized.equals(pattern)) {
                            result.put(method + " " + normalized, info);
                        }
                    }
                }
            }
        }

        return result;
    }
}
