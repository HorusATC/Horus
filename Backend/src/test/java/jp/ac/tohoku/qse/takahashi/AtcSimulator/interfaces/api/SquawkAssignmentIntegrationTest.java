package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.aggregate.airspace.AirspaceManagement;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;

@SpringBootTest
@AutoConfigureMockMvc
public class SquawkAssignmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AirspaceManagement airspaceManagement;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        airspaceManagement.removeAllAircrafts();
    }

    @Test
    void testAssignEmergencySquawk7700() throws Exception {
        String scenarioJson = """
            {
              "aircraft": [
                {
                  "callsign": "JAL123",
                  "aircraftType": "B773",
                  "model": "B777-300",
                  "position": { "latitude": 35.5, "longitude": 139.5, "altitude": 10000 },
                  "vector": { "heading": 90, "groundSpeed": 300, "verticalSpeed": 0 },
                  "originIata": "HND", "originIcao": "RJTT",
                  "destinationIata": "ITM", "destinationIcao": "RJOO",
                  "flightPlan": {
                    "waypoints": [],
                    "cruiseAltitude": 10000,
                    "cruiseSpeed": 300
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/scenario/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson))
                .andExpect(status().isOk());

        String squawkJson = """
            { "squawk": "7700" }
            """;

        mockMvc.perform(post("/api/aircraft/JAL123/squawk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(squawkJson))
                .andExpect(status().isOk());

        String result = mockMvc.perform(post("/api/aircraft/location/all"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode aircraftArray = objectMapper.readTree(result);
        assertEquals(1, aircraftArray.size());

        JsonNode aircraft = aircraftArray.get(0);
        assertEquals("JAL123", aircraft.get("callsign").asText());
        assertNotNull(aircraft.get("squawk"));
        assertEquals("7700", aircraft.get("squawk").asText());
    }

    @Test
    void testAssignSquawk7500Hijack() throws Exception {
        String scenarioJson = """
            {
              "aircraft": [
                {
                  "callsign": "ANA456",
                  "aircraftType": "B773",
                  "model": "B777-300",
                  "position": { "latitude": 35.5, "longitude": 139.5, "altitude": 10000 },
                  "vector": { "heading": 90, "groundSpeed": 300, "verticalSpeed": 0 },
                  "originIata": "HND", "originIcao": "RJTT",
                  "destinationIata": "ITM", "destinationIcao": "RJOO",
                  "flightPlan": {
                    "waypoints": [],
                    "cruiseAltitude": 10000,
                    "cruiseSpeed": 300
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/scenario/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson))
                .andExpect(status().isOk());

        String squawkJson = """
            { "squawk": "7500" }
            """;

        mockMvc.perform(post("/api/aircraft/ANA456/squawk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(squawkJson))
                .andExpect(status().isOk());

        String result = mockMvc.perform(post("/api/aircraft/location/all"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode aircraftArray = objectMapper.readTree(result);
        JsonNode aircraft = aircraftArray.get(0);
        assertEquals("7500", aircraft.get("squawk").asText());
    }

    @Test
    void testAssignSquawk7600RadioFailure() throws Exception {
        String scenarioJson = """
            {
              "aircraft": [
                {
                  "callsign": "SKY789",
                  "aircraftType": "B773",
                  "model": "B777-300",
                  "position": { "latitude": 35.5, "longitude": 139.5, "altitude": 10000 },
                  "vector": { "heading": 90, "groundSpeed": 300, "verticalSpeed": 0 },
                  "originIata": "HND", "originIcao": "RJTT",
                  "destinationIata": "ITM", "destinationIcao": "RJOO",
                  "flightPlan": {
                    "waypoints": [],
                    "cruiseAltitude": 10000,
                    "cruiseSpeed": 300
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/scenario/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson))
                .andExpect(status().isOk());

        String squawkJson = """
            { "squawk": "7600" }
            """;

        mockMvc.perform(post("/api/aircraft/SKY789/squawk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(squawkJson))
                .andExpect(status().isOk());

        String result = mockMvc.perform(post("/api/aircraft/location/all"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode aircraftArray = objectMapper.readTree(result);
        JsonNode aircraft = aircraftArray.get(0);
        assertEquals("7600", aircraft.get("squawk").asText());
    }

    @Test
    void testAssignNormalSquawk() throws Exception {
        String scenarioJson = """
            {
              "aircraft": [
                {
                  "callsign": "JJP100",
                  "aircraftType": "B773",
                  "model": "B777-300",
                  "position": { "latitude": 35.5, "longitude": 139.5, "altitude": 10000 },
                  "vector": { "heading": 90, "groundSpeed": 300, "verticalSpeed": 0 },
                  "originIata": "HND", "originIcao": "RJTT",
                  "destinationIata": "ITM", "destinationIcao": "RJOO",
                  "flightPlan": {
                    "waypoints": [],
                    "cruiseAltitude": 10000,
                    "cruiseSpeed": 300
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/scenario/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson))
                .andExpect(status().isOk());

        String squawkJson = """
            { "squawk": "1200" }
            """;

        mockMvc.perform(post("/api/aircraft/JJP100/squawk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(squawkJson))
                .andExpect(status().isOk());

        String result = mockMvc.perform(post("/api/aircraft/location/all"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode aircraftArray = objectMapper.readTree(result);
        JsonNode aircraft = aircraftArray.get(0);
        assertEquals("1200", aircraft.get("squawk").asText());
    }

    @Test
    void testInvalidSquawkRejected() throws Exception {
        String scenarioJson = """
            {
              "aircraft": [
                {
                  "callsign": "TEST001",
                  "aircraftType": "B773",
                  "model": "B777-300",
                  "position": { "latitude": 35.5, "longitude": 139.5, "altitude": 10000 },
                  "vector": { "heading": 90, "groundSpeed": 300, "verticalSpeed": 0 },
                  "originIata": "HND", "originIcao": "RJTT",
                  "destinationIata": "ITM", "destinationIcao": "RJOO",
                  "flightPlan": {
                    "waypoints": [],
                    "cruiseAltitude": 10000,
                    "cruiseSpeed": 300
                  }
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/scenario/load")
                .contentType(MediaType.APPLICATION_JSON)
                .content(scenarioJson))
                .andExpect(status().isOk());

        String invalidSquawkJson = """
            { "squawk": "9999" }
            """;

        mockMvc.perform(post("/api/aircraft/TEST001/squawk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidSquawkJson))
                .andExpect(status().isBadRequest());
    }
}
