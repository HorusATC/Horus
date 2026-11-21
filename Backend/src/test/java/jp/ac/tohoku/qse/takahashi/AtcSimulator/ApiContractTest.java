package jp.ac.tohoku.qse.takahashi.AtcSimulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
 * This test prevents issues like PR #119 where tests called non-existent
 * endpoints (/api/aircraft/location/all with wrong method).
 */
@SpringBootTest
class ApiContractTest {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private static final Map<String, String> FRONTEND_BFF_ENDPOINTS = Map.ofEntries(
            Map.entry("GET /aircraft/location/all", "aircraft/location/all/route.ts"),
            Map.entry("POST /api/aircraft/spawn-with-flightplan", "aircraft/spawn-with-flightplan/route.ts"),
            Map.entry("POST /api/aircraft/create-haneda-samples", "aircraft/create-haneda-samples/route.ts"),
            Map.entry("POST /api/aircraft/control/{callsign}", "aircraft/control/[callsign]/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/atc-clearance", "aircraft/[callsign]/atc-clearance/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/squawk", "aircraft/[callsign]/squawk/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/direct-to", "aircraft/[callsign]/direct-to/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/hold", "aircraft/[callsign]/hold/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/final-approach", "aircraft/[callsign]/final-approach/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/go-around", "aircraft/[callsign]/go-around/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/tower-handoff", "aircraft/[callsign]/tower-handoff/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/resume-navigation", "aircraft/[callsign]/resume-navigation/route.ts"),
            Map.entry("GET /api/aircraft/{callsign}/flightplan", "aircraft/[callsign]/flightplan/route.ts"),
            Map.entry("POST /api/aircraft/{callsign}/flightplan", "aircraft/[callsign]/flightplan/route.ts"),
            Map.entry("POST /api/scenario/load", "scenario/load/route.ts"),
            Map.entry("POST /simulation/start", "simulation/[action]/route.ts"),
            Map.entry("POST /simulation/pause", "simulation/[action]/route.ts"),
            Map.entry("GET /simulation/speed", "simulation/speed/route.ts"),
            Map.entry("PUT /simulation/speed", "simulation/speed/route.ts"),
            Map.entry("GET /ats/route/all", "ats/route/all/route.ts"),
            Map.entry("GET /ats/route/suggest", "ats/route/suggest/route.ts"),
            Map.entry("GET /ats/airports", "ats/airports/route.ts"),
            Map.entry("GET /ats/runways", "ats/runways/route.ts"),
            Map.entry("GET /api/conflict/all", "conflict/all/route.ts"),
            Map.entry("GET /api/conflict/critical", "conflict/critical/route.ts"),
            Map.entry("GET /api/conflict/statistics", "conflict/statistics/route.ts"),
            Map.entry("GET /api/conflict/violations", "conflict/violations/route.ts"),
            Map.entry("GET /api/conflict/health", "conflict/health/route.ts"),
            Map.entry("GET /api/conflict/filtered", "conflict/filtered/route.ts"),
            Map.entry("GET /api/conflict/aircraft/{callsign}", "conflict/aircraft/[callsign]/route.ts")
    );

    private static final Map<String, String> BACKEND_TEST_ENDPOINTS = Map.ofEntries(
            Map.entry("POST /api/scenario/load", "FlightPlanApiIntegrationTest"),
            Map.entry("POST /simulation/start", "FlightPlanApiIntegrationTest"),
            Map.entry("POST /simulation/pause", "SimulationSpeedIntegrationTest"),
            Map.entry("GET /simulation/status", "SimulationSpeedIntegrationTest"),
            Map.entry("PUT /simulation/speed", "SimulationSpeedIntegrationTest"),
            Map.entry("GET /simulation/speed", "SimulationSpeedIntegrationTest")
    );

    @Test
    void allFrontendBffCallsShouldMatchActualSpringMappings() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();

        assertThat(FRONTEND_BFF_ENDPOINTS)
                .as("Frontend BFF endpoint list should not be empty")
                .hasSizeGreaterThanOrEqualTo(20);

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : FRONTEND_BFF_ENDPOINTS.entrySet()) {
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
    void allBackendTestCallsShouldMatchActualSpringMappings() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();

        List<String> missingEndpoints = new ArrayList<>();
        for (Map.Entry<String, String> entry : BACKEND_TEST_ENDPOINTS.entrySet()) {
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

    @Test
    void shouldDetectWrongEndpointPath_PR119Example() {
        Map<String, RequestMappingInfo> actualMappings = getActualSpringMappings();

        String wrongEndpoint = "GET /api/aircraft/location/all";

        assertThat(actualMappings)
                .as("PR #119 used wrong path: /api/aircraft/location/all (should be /aircraft/location/all)")
                .doesNotContainKey(wrongEndpoint);

        String correctEndpoint = "GET /aircraft/location/all";
        assertThat(actualMappings)
                .as("Correct endpoint should exist")
                .containsKey(correctEndpoint);
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
