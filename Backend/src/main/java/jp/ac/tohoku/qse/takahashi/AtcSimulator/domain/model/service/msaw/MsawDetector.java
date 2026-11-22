package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import java.util.List;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.Aircraft;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.departure.InitialClimbGeometry;
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

        if (aircraft instanceof AircraftBase base
                && base.isInitialClimbActive()
                && isWithinDepartureClimbCorridor(base, position)) {
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

    private boolean isWithinDepartureClimbCorridor(AircraftBase base, AircraftPosition position) {
        Double startLat = base.getInitialClimbStartLatitude();
        Double startLon = base.getInitialClimbStartLongitude();
        Double runwayHeading = base.getInitialClimbRunwayHeadingDeg();
        if (startLat == null || startLon == null || runwayHeading == null) {
            return false;
        }
        AircraftPosition start = new AircraftPosition(
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude(startLat),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude(startLon),
                new jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude(0.0));
        double distanceNm = GeodeticUtils.calculateHorizontalDistance(position, start);
        double bearing = InitialClimbGeometry.bearingDeg(
                startLat,
                startLon,
                position.latitude.toDouble(),
                position.longitude.toDouble());
        return InitialClimbGeometry.isWithinDepartureCorridor(
                startLat,
                startLon,
                runwayHeading,
                position.latitude.toDouble(),
                position.longitude.toDouble(),
                distanceNm,
                bearing);
    }

    private ApproachCorridorCheck checkApproachCorridor(AircraftPosition position) {
        double corridorWidthNm = msaCatalog.getApproachCorridorWidthNm();
        double extensionNm = msaCatalog.getApproachCorridorExtensionNm();

        ClosestCenterlineRunway.Selection selected =
                ClosestCenterlineRunway.selectClosestCenterlineRunway(
                        position,
                        runwayCatalog.getAllRunways(),
                        corridorWidthNm,
                        extensionNm);
        if (selected == null) {
            return new ApproachCorridorCheck(false, 0, 0);
        }

        double elevationFt = msaCatalog.getAirportElevationFt(selected.airportIcao());
        return new ApproachCorridorCheck(true, selected.alongTrackNm(), elevationFt);
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

    private record ApproachCorridorCheck(
            boolean isInCorridor,
            double distanceFromThresholdNm,
            double airportElevationFt) {}
}
