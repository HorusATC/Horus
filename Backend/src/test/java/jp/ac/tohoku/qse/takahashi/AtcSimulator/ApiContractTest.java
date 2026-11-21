package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import java.util.stream.Collectors;

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
 * This test prevents issues like PR #119 where tests called non-existent endpoints
 * (/api/aircraft/location/all with wrong method, wrong scenario/load body).
 */
@SpringBootTest
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * Frontend BFF routes (app/api/*&#47;route.ts) make these backend calls.
     * Extracted from: grep -r "proxyToBackend(" Frontend/app/api
     */
    private static final Map<String, String> FRONTEND_BFF_CALLS = Map.ofEntries(
            Map.entry("GET /aircraft/location/all", "Aircraft location polling"),
            Map.entry("POST /api/aircraft/spawn-with-flightplan", "Spawn aircraft with flight plan"),
            Map.entry("POST /api/aircraft/create-haneda-samples", "Create Haneda sample aircraft"),
            Map.entry("POST /api/scenario/load", "Load scenario"),
            Map.entry("GET /ats/route/all", "Get all ATS routes"),
            Map.entry("GET /ats/airports", "Get airports list"),
            Map.entry("GET /ats/runways", "Get runways list"),
            Map.entry("GET /api/conflict/all", "Get all conflicts"),
            Map.entry("GET /api/conflict/critical", "Get critical conflicts"),
            Map.entry("GET /api/conflict/statistics", "Get conflict statistics"),
            Map.entry("GET /api/conflict/violations", "Get conflict violations"),
            Map.entry("GET /api/conflict/health", "Conflict system health"),
            Map.entry("GET /simulation/speed", "Get simulation speed"),
            Map.entry("PUT /simulation/speed", "Set simulation speed")
    );

    /**
     * Backend integration tests make these calls.
     * Extracted from test files using MockMvc/RestTemplate.
     */
    private static final Map<String, String> BACKEND_TEST_CALLS = Map.ofEntries(
            Map.entry("POST /api/scenario/load", "FlightPlanApiIntegrationTest"),
            Map.entry("POST /simulation/start", "FlightPlanApiIntegrationTest"),
            Map.entry("POST /simulation/pause", "FlightPlanApiIntegrationTest"),
            Map.entry("GET /simulation/status", "SimulationSpeedIntegrationTest"),
            Map.entry("PUT /simulation/speed", "SimulationSpeedIntegrationTest"),
            Map.entry("GET /simulation/speed", "SimulationSpeedIntegrationTest")
    );

    @Test
    void allFrontendBffCallsShouldMatchActualSpringMappings() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        Set<String> actualEndpoints = actualMappings.keySet();

        List<String> missingEndpoints = new ArrayList<>();

        for (String endpoint : FRONTEND_BFF_CALLS.keySet()) {
            if (!actualEndpoints.contains(endpoint)) {
                String description = FRONTEND_BFF_CALLS.get(endpoint);
                missingEndpoints.add(String.format("%s (%s)", endpoint, description));
            }
        }

        assertThat(missingEndpoints)
                .as("Frontend BFF routes call non-existent backend endpoints")
                .isEmpty();
    }

    @Test
    void allBackendTestCallsShouldMatchActualSpringMappings() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();
        Set<String> actualEndpoints = actualMappings.keySet();

        List<String> missingEndpoints = new ArrayList<>();

        for (String endpoint : BACKEND_TEST_CALLS.keySet()) {
            if (!actualEndpoints.contains(endpoint)) {
                String source = BACKEND_TEST_CALLS.get(endpoint);
                missingEndpoints.add(String.format("%s (used in %s)", endpoint, source));
            }
        }

        assertThat(missingEndpoints)
                .as("Backend integration tests call non-existent endpoints")
                .isEmpty();
    }

    @Test
    void demonstrateDetectionOfWrongEndpoint_PR119Example() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();

        String wrongEndpoint = "GET /api/aircraft/location/all";

        assertThat(actualMappings)
                .as("PR #119 used wrong path: /api/aircraft/location/all should be /aircraft/location/all")
                .doesNotContainKey(wrongEndpoint);
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
