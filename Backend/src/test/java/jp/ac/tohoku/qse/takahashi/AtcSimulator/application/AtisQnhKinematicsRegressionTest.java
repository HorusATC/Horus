package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.shared.utility.IndicatedAirspeedConverter;

/**
 * ATIS QNH is observational only: IAS→TAS still uses pressure altitude as altitude,
 * with no QNH correction (spec/20261122-atis-qnh).
 */
class AtisQnhKinematicsRegressionTest {

    @Test
    void indicatedToTrueAirspeedIgnoresQnhConcept() {
        double ias = 250.0;
        double altitudeFt = 10_000.0;
        double tasBefore = IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(ias, altitudeFt);
        // Converter API takes only IAS and altitude; QNH cannot alter TAS without a signature change.
        double tasAfter = IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(ias, altitudeFt);
        assertEquals(tasBefore, tasAfter, 0.0);
        assertEquals(
                IndicatedAirspeedConverter.indicatedToTrueAirspeedKnots(ias, altitudeFt),
                tasBefore,
                0.0);
    }
}
