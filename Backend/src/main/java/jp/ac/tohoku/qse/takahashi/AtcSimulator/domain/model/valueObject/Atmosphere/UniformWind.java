package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere;

import java.util.Objects;
import java.util.Optional;

/**
 * Session-wide horizontal wind. Direction is the true bearing the wind blows from (PHAK / JMA 5.1).
 * Missing means no observation (legacy ATIS line); Calm means observed calm (0 kt).
 */
public final class UniformWind {

    public static final int MAX_SPEED_KT = 99;
    public static final int MIN_DIRECTION_DEG = 1;
    public static final int MAX_DIRECTION_DEG = 360;

    private enum Kind {
        MISSING,
        CALM,
        MEASURED
    }

    private final Kind kind;
    private final int directionDeg;
    private final int speedKt;

    private UniformWind(Kind kind, int directionDeg, int speedKt) {
        this.kind = kind;
        this.directionDeg = directionDeg;
        this.speedKt = speedKt;
    }

    public static UniformWind missing() {
        return new UniformWind(Kind.MISSING, 0, 0);
    }

    public static UniformWind calm() {
        return new UniformWind(Kind.CALM, 0, 0);
    }

    public static UniformWind measured(int directionDeg, int speedKt) {
        if (speedKt < 0 || speedKt > MAX_SPEED_KT) {
            throw new IllegalArgumentException(
                    "wind speedKt must be 0.." + MAX_SPEED_KT + ", got " + speedKt);
        }
        if (speedKt == 0) {
            return calm();
        }
        if (directionDeg < MIN_DIRECTION_DEG || directionDeg > MAX_DIRECTION_DEG) {
            throw new IllegalArgumentException(
                    "wind directionDeg must be "
                            + MIN_DIRECTION_DEG
                            + ".."
                            + MAX_DIRECTION_DEG
                            + " (true), got "
                            + directionDeg);
        }
        return new UniformWind(Kind.MEASURED, directionDeg, speedKt);
    }

    public boolean isMissing() {
        return kind == Kind.MISSING;
    }

    public boolean isCalm() {
        return kind == Kind.CALM;
    }

    public boolean affectsMotion() {
        return kind == Kind.MEASURED && speedKt > 0;
    }

    public Optional<Integer> directionDeg() {
        if (kind != Kind.MEASURED) {
            return Optional.empty();
        }
        return Optional.of(directionDeg);
    }

    public Optional<Integer> speedKt() {
        return switch (kind) {
            case MISSING -> Optional.empty();
            case CALM -> Optional.of(0);
            case MEASURED -> Optional.of(speedKt);
        };
    }

    public int speedKnotsOrZero() {
        return kind == Kind.MEASURED ? speedKt : 0;
    }

    public int directionDegOrZero() {
        return kind == Kind.MEASURED ? directionDeg : 0;
    }

    public boolean sameObservation(UniformWind other) {
        if (other == null) {
            return false;
        }
        if (kind != other.kind) {
            return false;
        }
        if (kind == Kind.MEASURED) {
            return directionDeg == other.directionDeg && speedKt == other.speedKt;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UniformWind that)) {
            return false;
        }
        return kind == that.kind && directionDeg == that.directionDeg && speedKt == that.speedKt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, directionDeg, speedKt);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case MISSING -> "UniformWind[missing]";
            case CALM -> "UniformWind[calm]";
            case MEASURED -> "UniformWind[from=" + directionDeg + ", " + speedKt + "kt]";
        };
    }
}
