package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * Discriminated altitude constraint for vertical guidance.
 * Catalog / flight-plan rows still use {@link AltitudeConstraint}; map through
 * {@link VerticalConstraints}.
 */
public sealed interface VerticalConstraint
        permits VerticalConstraint.None,
                VerticalConstraint.At,
                VerticalConstraint.AtOrAbove,
                VerticalConstraint.AtOrBelow,
                VerticalConstraint.Window {

    record None() implements VerticalConstraint {}

    record At(double altitudeFt) implements VerticalConstraint {
        public At {
            if (!Double.isFinite(altitudeFt)) {
                throw new IllegalArgumentException("altitudeFt must be finite");
            }
        }
    }

    record AtOrAbove(double altitudeFt) implements VerticalConstraint {
        public AtOrAbove {
            if (!Double.isFinite(altitudeFt)) {
                throw new IllegalArgumentException("altitudeFt must be finite");
            }
        }
    }

    record AtOrBelow(double altitudeFt) implements VerticalConstraint {
        public AtOrBelow {
            if (!Double.isFinite(altitudeFt)) {
                throw new IllegalArgumentException("altitudeFt must be finite");
            }
        }
    }

    record Window(double minFt, double maxFt) implements VerticalConstraint {
        public Window {
            if (!Double.isFinite(minFt) || !Double.isFinite(maxFt)) {
                throw new IllegalArgumentException("window bounds must be finite");
            }
            if (minFt > maxFt) {
                throw new IllegalArgumentException("window minFt must be <= maxFt");
            }
        }
    }
}
