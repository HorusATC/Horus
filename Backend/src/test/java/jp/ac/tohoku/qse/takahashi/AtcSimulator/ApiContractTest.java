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

    @Test
    void allFrontendBffCallsShouldMatchActualSpringMappings() throws IOException {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        Map<String, String> frontendCalls = extractFrontendBffCalls();

        assertThat(frontendCalls)
                .as("Frontend BFF extraction should find ≥20 endpoints (found " + frontendCalls.size() + ")")
                .hasSizeGreaterThanOrEqualTo(20);

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
        Map<String, String> backendTestCalls = extractBackendTestCalls();

        assertThat(backendTestCalls)
                .as("Backend test extraction found " + backendTestCalls.size() + " endpoints")
                .hasSizeGreaterThanOrEqualTo(0);

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : backendTestCalls.entrySet()) {
            String endpoint = entry.getKey();
            String sourceFile = entry.getValue();

            if (!matchesAnyMapping(endpoint, actualMappings)) {
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

                    if (proxyMatcher.find()) {
                        String path = proxyMatcher.group(2);
                        path = normalizePath(path);
                        calls.put(method + " " + path, relPath);
                    }

                    Pattern fetchPattern = Pattern.compile(
                            "fetch\\s*\\(\\s*`([^`]+)`");
                    Matcher fetchMatcher = fetchPattern.matcher(functionBody);

                    if (fetchMatcher.find()) {
                        String path = fetchMatcher.group(1);
                        path = normalizePath(path);
                        calls.put(method + " " + path, relPath);
                    }
                }
            }
        }

        return calls;
    }

    private String normalizePath(String path) {
        path = path.replaceAll("\\?.*$", "");
        path = path.replaceAll("\\$\\{encodeURIComponent\\([^)]+\\)\\}", "{callsign}");
        path = path.replaceAll("\\$\\{callsign\\}", "{callsign}");
        path = path.replaceAll("\\$\\{action\\}", "{action}");
        path = path.replaceAll("\\$\\{[^}]+\\}", "{param}");
        return path;
    }

    private Map<String, String> extractBackendTestCalls() throws IOException {
        Map<String, String> calls = new LinkedHashMap<>();

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

                Pattern pathPattern = Pattern.compile("[\"'](/api/[^\"']+)[\"']");
                Matcher pathMatcher = pathPattern.matcher(content);

                while (pathMatcher.find()) {
                    String path = pathMatcher.group(1);
                    int pos = pathMatcher.start();
                    String before = content.substring(Math.max(0, pos - 100), pos);

                    String method = "";
                    if (before.contains("postForEntity")) method = "POST";
                    else if (before.contains("getForEntity")) method = "GET";
                    else if (before.contains("putForObject")) method = "PUT";
                    else if (before.contains("delete")) method = "DELETE";

                    if (!method.isEmpty()) {
                        path = normalizePath(path);
                        calls.put(method + " " + path, fileName);
                    }
                }
            }
        }

        return calls;
    }

    private boolean matchesAnyMapping(String endpoint, Map<String, RequestMappingInfo> mappings) {
        if (mappings.containsKey(endpoint)) {
            return true;
        }

        String[] parts = endpoint.split(" ", 2);
        if (parts.length != 2) {
            return false;
        }
        String method = parts[0];
        String path = parts[1];

        for (String mappingKey : mappings.keySet()) {
            String[] mappingParts = mappingKey.split(" ", 2);
            if (mappingParts.length != 2) continue;

            String mappingMethod = mappingParts[0];
            String mappingPath = mappingParts[1];

            if (method.equals(mappingMethod) && pathsMatch(path, mappingPath)) {
                return true;
            }
        }

        return false;
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
}
