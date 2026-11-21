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
                .as("Backend test extraction should find endpoints (found " + backendTestCalls.size() + ")")
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

                Pattern proxyPattern = Pattern.compile(
                        "return\\s+proxyToBackend\\s*\\(\\s*[`\"]([^`\"]+)[`\"]\\s*,\\s*\\{[^}]*?method:\\s*[\"']([A-Z]+)[\"']",
                        Pattern.DOTALL);
                Matcher proxyMatcher = proxyPattern.matcher(content);
                while (proxyMatcher.find()) {
                    String path = proxyMatcher.group(1);
                    String method = proxyMatcher.group(2);
                    path = normalizePathParams(path);
                    calls.put(method + " " + path, relPath);
                }

                Pattern fetchPattern = Pattern.compile(
                        "(?:await\\s+)?fetch\\s*\\(\\s*`([^`]+)`[^)]*?method:\\s*[\"']([A-Z]+)[\"']",
                        Pattern.DOTALL);
                Matcher fetchMatcher = fetchPattern.matcher(content);
                while (fetchMatcher.find()) {
                    String path = fetchMatcher.group(1);
                    String method = fetchMatcher.group(2);
                    path = normalizePathParams(path);
                    calls.put(method + " " + path, relPath);
                }
            }
        }

        return calls;
    }

    private String normalizePathParams(String path) {
        path = path.replaceAll("\\$\\{encodeURIComponent\\([^)]+\\)\\}", "{callsign}");
        path = path.replaceAll("\\$\\{callsign\\}", "{callsign}");
        path = path.replaceAll("\\$\\{[^}]+\\}", "{callsign}");
        return path;
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

                Pattern pattern = Pattern.compile("\\.(?:get|post|put|delete|patch)\\(\\)\\s*\\.uri\\([\"']([^\"']+)[\"']\\)");
                Matcher matcher = pattern.matcher(content);

                while (matcher.find()) {
                    String fullMatch = matcher.group(0);
                    String path = matcher.group(1);
                    String method = "";

                    if (fullMatch.contains(".get()")) {
                        method = "GET";
                    } else if (fullMatch.contains(".post()")) {
                        method = "POST";
                    } else if (fullMatch.contains(".put()")) {
                        method = "PUT";
                    } else if (fullMatch.contains(".delete()")) {
                        method = "DELETE";
                    } else if (fullMatch.contains(".patch()")) {
                        method = "PATCH";
                    }

                    if (!method.isEmpty()) {
                        calls.put(method + " " + path, fileName);
                    }
                }
            }
        }

        return calls;
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
                        String normalized = pattern.replaceAll("\\{[^}]+\\}", "{callsign}");
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
