package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import java.util.List;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawAlert;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawAlertType;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw.MsawTrigger;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftVector;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.MsaCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.RunwayCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

public class MsawDetector {

    /**
     * Altitudes within this band of a floor are treated as not below it, so
     * exact-on-floor and floating-point noise near the floor do not alert.
     */
    static final double ALTITUDE_FLOOR_EPSILON_FT = 0.5;

    private final MsaCatalog msaCatalog;
    private final RunwayCatalog runwayCatalog;

    public MsawDetector(MsaCatalog msaCatalog, RunwayCatalog runwayCatalog) {
        this.msaCatalog = msaCatalog;
        this.runwayCatalog = runwayCatalog;
    }

    static boolean isBelowMinimum(double altitudeFt, double minimumFt) {
        return altitudeFt < minimumFt - ALTITUDE_FLOOR_EPSILON_FT;
    }

    public MsawAlert detectAlert(Aircraft aircraft) {
        if (aircraft == null) {
            return null;
        }

        AircraftPosition position = aircraft.getAircraftPosition();
        AircraftVector vector = aircraft.getAircraftVector();

        if (isWithinSuppressionZone(position)) {
            return null;
        }

        ApproachCorridorCheck corridorCheck = checkApproachCorridor(position);
        if (corridorCheck.isInCorridor) {
            return checkApproachFloor(position, vector, corridorCheck);
        }

        return checkAreaMinimumAltitude(position, vector);
    }

    private boolean isWithinSuppressionZone(AircraftPosition position) {
        double suppressionNm = msaCatalog.getSuppressionDistanceNm();

        for (var runway : runwayCatalog.getAllRunways()) {
            double distanceNm = GeodeticUtils.calculateHorizontalDistance(
                    position,
                    new AircraftPosition(
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(runway.thresholdLatitude()),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(runway.thresholdLongitude()),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude(0.0)
                    )
            );

            if (distanceNm < suppressionNm) {
                return true;
            }
        }
        return false;
    }

    private ApproachCorridorCheck checkApproachCorridor(AircraftPosition position) {
        double corridorWidthNm = msaCatalog.getApproachCorridorWidthNm();
        double extensionNm = msaCatalog.getApproachCorridorExtensionNm();

        for (var runway : runwayCatalog.getAllRunways()) {
            if (!runway.usableForLanding()) {
                continue;
            }
            var geometry = runwayCatalog.buildFinalApproachGeometry(runway);
            if (geometry.isEmpty()) {
                continue;
            }

            var geom = geometry.get();
            double thresholdLat = runway.thresholdLatitude();
            double thresholdLon = runway.thresholdLongitude();
            double approachHeading = geom.approachCourseHeading();

            double distanceFromThresholdNm = GeodeticUtils.calculateHorizontalDistance(
                    position,
                    new AircraftPosition(
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(thresholdLat),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(thresholdLon),
                            new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude(0.0)
                    )
            );

            double bearing = calculateBearing(thresholdLat, thresholdLon,
                    position.latitude.toDouble(), position.longitude.toDouble());
            double reciprocalHeading = (approachHeading + 180.0) % 360.0;
            double bearingDiff = Math.abs(normalizeAngle(bearing - reciprocalHeading));

            if (bearingDiff > 90.0) {
                continue;
            }

            double projectedDistanceNm = distanceFromThresholdNm * Math.cos(Math.toRadians(bearingDiff));
            if (projectedDistanceNm < 0 || projectedDistanceNm > extensionNm) {
                continue;
            }

            double lateralOffsetNm = distanceFromThresholdNm * Math.sin(Math.toRadians(bearingDiff));
            if (Math.abs(lateralOffsetNm) <= corridorWidthNm) {
                String airportIcao = runway.airportIcao();
                double elevationFt = msaCatalog.getAirportElevationFt(airportIcao);
                return new ApproachCorridorCheck(true, projectedDistanceNm, elevationFt);
            }
        }

        return new ApproachCorridorCheck(false, 0, 0);
    }

    private MsawAlert checkApproachFloor(AircraftPosition position, AircraftVector vector,
                                         ApproachCorridorCheck corridorCheck) {
        double gradientFtPerNm = msaCatalog.getApproachFloorGradientFtPerNm();
        double floorFt = corridorCheck.airportElevationFt +
                gradientFtPerNm * corridorCheck.distanceFromThresholdNm;

        if (isBelowMinimum(position.altitude.toDouble(), floorFt)) {
            return new MsawAlert(MsawAlertType.LOW, floorFt, MsawTrigger.CURRENT);
        }

        AircraftPosition predictedPosition = GeodeticUtils.predictPosition(
                position, vector, msaCatalog.getPredictionTimeSeconds());

        ApproachCorridorCheck predictedCorridor = checkApproachCorridor(predictedPosition);
        if (predictedCorridor.isInCorridor) {
            double predictedFloorFt = predictedCorridor.airportElevationFt +
                    gradientFtPerNm * predictedCorridor.distanceFromThresholdNm;

            if (isBelowMinimum(predictedPosition.altitude.toDouble(), predictedFloorFt)) {
                return new MsawAlert(MsawAlertType.LOW, predictedFloorFt, MsawTrigger.PREDICTED);
            }
        }

        return null;
    }

    private MsawAlert checkAreaMinimumAltitude(AircraftPosition position, AircraftVector vector) {
        Double highestMinimum = findHighestMinimumAltitude(position);

        if (highestMinimum != null && isBelowMinimum(position.altitude.toDouble(), highestMinimum)) {
            return new MsawAlert(MsawAlertType.MSA, highestMinimum, MsawTrigger.CURRENT);
        }

        AircraftPosition predictedPosition = GeodeticUtils.predictPosition(
                position, vector, msaCatalog.getPredictionTimeSeconds());
        Double predictedHighestMinimum = findHighestMinimumAltitude(predictedPosition);

        if (predictedHighestMinimum != null
                && isBelowMinimum(predictedPosition.altitude.toDouble(), predictedHighestMinimum)) {
            return new MsawAlert(MsawAlertType.MSA, predictedHighestMinimum, MsawTrigger.PREDICTED);
        }

        return null;
    }

    private Double findHighestMinimumAltitude(AircraftPosition position) {
        double lat = position.latitude.toDouble();
        double lon = position.longitude.toDouble();

        Double highestMinimum = null;

        for (var area : msaCatalog.getAreas()) {
            if (isPointInPolygon(lat, lon, area.polygon())) {
                if (highestMinimum == null || area.minimumAltitudeFt() > highestMinimum) {
                    highestMinimum = area.minimumAltitudeFt();
                }
            }
        }

        return highestMinimum;
    }

    private boolean isPointInPolygon(double lat, double lon, List<MsaCatalog.LatLon> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        int intersections = 0;
        int n = polygon.size();

        for (int i = 0; i < n; i++) {
            MsaCatalog.LatLon v1 = polygon.get(i);
            MsaCatalog.LatLon v2 = polygon.get((i + 1) % n);

            if ((v1.longitude() <= lon && lon < v2.longitude()) ||
                    (v2.longitude() <= lon && lon < v1.longitude())) {

                double latAtLon = v1.latitude() +
                        (lon - v1.longitude()) * (v2.latitude() - v1.latitude()) /
                                (v2.longitude() - v1.longitude());

                if (lat < latAtLon) {
                    intersections++;
                }
            }
        }

        return (intersections % 2) == 1;
    }

    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLonRad = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLonRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad);

        double bearingRad = Math.atan2(y, x);
        return (Math.toDegrees(bearingRad) + 360.0) % 360.0;
    }

    private double normalizeAngle(double angle) {
        double normalized = angle % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        if (normalized > 180.0) {
            normalized = 360.0 - normalized;
        }
        return normalized;
    }

    private record ApproachCorridorCheck(
            boolean isInCorridor,
            double distanceFromThresholdNm,
            double airportElevationFt) {}
}
