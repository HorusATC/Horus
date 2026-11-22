package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.service.msaw;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.airport.RunwayDefinition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Altitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Latitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.AircraftAttributes.Longitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Position.AircraftPosition;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.GeodeticUtils;

/**
 * Shared MSAW / approach-corridor runway selection: among landing-usable ends whose
 * corridor contains the point, pick minimum |cross-track|, then shorter along-track.
 * Greppable single selection implementation for the Backend.
 */
public final class ClosestCenterlineRunway {

    private ClosestCenterlineRunway() {}

    public record Selection(
            String airportIcao,
            String designator,
            double approachCourseHeading,
            double alongTrackNm,
            double crossTrackNm) {}

    /**
     * Select the closest centerline runway for {@code position}.
     * Corridor dims must match Frontend {@code APPROACH_CORRIDOR_*} / msa-areas.json.
     */
    public static Selection selectClosestCenterlineRunway(
            AircraftPosition position,
            Iterable<RunwayDefinition> runways,
            double corridorWidthNm,
            double extensionNm) {
        Selection best = null;
        double lat = position.latitude.toDouble();
        double lon = position.longitude.toDouble();

        for (RunwayDefinition runway : runways) {
            if (!runway.usableForLanding()) {
                continue;
            }
            double thresholdLat = runway.thresholdLatitude();
            double thresholdLon = runway.thresholdLongitude();
            double approachHeading = normalizeHeading(runway.heading());

            double distanceFromThresholdNm = GeodeticUtils.calculateHorizontalDistance(
                    position,
                    new AircraftPosition(
                            new Latitude(thresholdLat),
                            new Longitude(thresholdLon),
                            new Altitude(0.0)));

            double bearing = bearingDeg(thresholdLat, thresholdLon, lat, lon);
            double reciprocalHeading = (approachHeading + 180.0) % 360.0;
            double bearingDiff = Math.abs(normalizeAngleDiff(bearing - reciprocalHeading));
            if (bearingDiff > 90.0) {
                continue;
            }

            double alongTrackNm = distanceFromThresholdNm * Math.cos(Math.toRadians(bearingDiff));
            if (alongTrackNm <= 0 || alongTrackNm > extensionNm) {
                continue;
            }

            double crossTrackNm = distanceFromThresholdNm * Math.sin(Math.toRadians(bearingDiff));
            if (Math.abs(crossTrackNm) > corridorWidthNm) {
                continue;
            }

            Selection candidate = new Selection(
                    runway.airportIcao(),
                    runway.designator(),
                    approachHeading,
                    alongTrackNm,
                    crossTrackNm);
            if (best == null) {
                best = candidate;
                continue;
            }
            double absXtk = Math.abs(candidate.crossTrackNm());
            double bestAbsXtk = Math.abs(best.crossTrackNm());
            if (absXtk < bestAbsXtk
                    || (absXtk == bestAbsXtk && candidate.alongTrackNm() < best.alongTrackNm())) {
                best = candidate;
            }
        }
        return best;
    }

    private static double normalizeHeading(double heading) {
        double normalized = heading % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        return normalized;
    }

    private static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLonRad = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLonRad) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLonRad);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private static double normalizeAngleDiff(double angle) {
        double normalized = angle % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        if (normalized > 180.0) {
            normalized = 360.0 - normalized;
        }
        return normalized;
    }
}
