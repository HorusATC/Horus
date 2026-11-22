package jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class MsaCatalog {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final double predictionTimeSeconds;
    private final double suppressionDistanceNm;
    private final double approachCorridorWidthNm;
    private final double approachCorridorExtensionNm;
    private final double approachFloorGradientFtPerNm;
    private final List<AirportElevation> airports;
    private final List<MsaArea> areas;

    public MsaCatalog() {
        var data = loadCatalog();
        this.predictionTimeSeconds = data.predictionTimeSeconds;
        this.suppressionDistanceNm = data.suppressionDistanceNm;
        this.approachCorridorWidthNm = data.approachCorridorWidthNm;
        this.approachCorridorExtensionNm = data.approachCorridorExtensionNm;
        this.approachFloorGradientFtPerNm = data.approachFloorGradientFtPerNm;
        this.airports = Collections.unmodifiableList(data.airports);
        this.areas = Collections.unmodifiableList(data.areas);
    }

    /** Constructor for tests that inject overlapping areas without changing classpath catalog. */
    public MsaCatalog(
            double predictionTimeSeconds,
            double suppressionDistanceNm,
            double approachCorridorWidthNm,
            double approachCorridorExtensionNm,
            double approachFloorGradientFtPerNm,
            List<AirportElevation> airports,
            List<MsaArea> areas) {
        this.predictionTimeSeconds = predictionTimeSeconds;
        this.suppressionDistanceNm = suppressionDistanceNm;
        this.approachCorridorWidthNm = approachCorridorWidthNm;
        this.approachCorridorExtensionNm = approachCorridorExtensionNm;
        this.approachFloorGradientFtPerNm = approachFloorGradientFtPerNm;
        this.airports = Collections.unmodifiableList(airports);
        this.areas = Collections.unmodifiableList(areas);
    }

    public double getPredictionTimeSeconds() {
        return predictionTimeSeconds;
    }

    public double getSuppressionDistanceNm() {
        return suppressionDistanceNm;
    }

    public double getApproachCorridorWidthNm() {
        return approachCorridorWidthNm;
    }

    public double getApproachCorridorExtensionNm() {
        return approachCorridorExtensionNm;
    }

    public double getApproachFloorGradientFtPerNm() {
        return approachFloorGradientFtPerNm;
    }

    public List<AirportElevation> getAirports() {
        return airports;
    }

    public List<MsaArea> getAreas() {
        return areas;
    }

    public double getAirportElevationFt(String icao) {
        return airports.stream()
                .filter(a -> a.icao.equalsIgnoreCase(icao))
                .findFirst()
                .map(a -> a.elevationFt)
                .orElse(0.0);
    }

    private static CatalogData loadCatalog() {
        try (InputStream is = new ClassPathResource("fix/msa-areas.json").getInputStream()) {
            JsonNode root = OBJECT_MAPPER.readTree(is);

            double predictionTime = root.get("predictionTimeSeconds").asDouble();
            double suppressionDistance = root.get("suppressionDistanceNm").asDouble();
            double corridorWidth = root.get("approachCorridorWidthNm").asDouble();
            double corridorExtension = root.get("approachCorridorExtensionNm").asDouble();
            double floorGradient = root.get("approachFloorGradientFtPerNm").asDouble();

            List<AirportElevation> airportList = new ArrayList<>();
            for (JsonNode node : root.get("airports")) {
                airportList.add(new AirportElevation(
                        node.get("icao").asText(),
                        node.get("name").asText(),
                        node.get("elevationFt").asDouble()));
            }

            List<MsaArea> areaList = new ArrayList<>();
            for (JsonNode node : root.get("areas")) {
                String id = node.get("id").asText();
                String name = node.get("name").asText();
                double minAlt = node.get("minimumAltitudeFt").asDouble();

                List<LatLon> polygon = new ArrayList<>();
                for (JsonNode vertex : node.get("polygon")) {
                    polygon.add(new LatLon(
                            vertex.get("latitude").asDouble(),
                            vertex.get("longitude").asDouble()));
                }
                areaList.add(new MsaArea(id, name, minAlt, polygon));
            }

            return new CatalogData(predictionTime, suppressionDistance, corridorWidth,
                    corridorExtension, floorGradient, airportList, areaList);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load msa-areas.json", e);
        }
    }

    private record CatalogData(
            double predictionTimeSeconds,
            double suppressionDistanceNm,
            double approachCorridorWidthNm,
            double approachCorridorExtensionNm,
            double approachFloorGradientFtPerNm,
            List<AirportElevation> airports,
            List<MsaArea> areas) {}

    public record AirportElevation(String icao, String name, double elevationFt) {}

    public record MsaArea(String id, String name, double minimumAltitudeFt, List<LatLon> polygon) {}

    public record LatLon(double latitude, double longitude) {}
}
