package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

/**
 * Optional wind change on ATIS update: omitted field, clear to missing, or set observation.
 */
public final class WindUpdate {

    public enum Kind {
        ABSENT,
        CLEAR,
        SET
    }

    private final Kind kind;
    private final UniformWind wind;

    private WindUpdate(Kind kind, UniformWind wind) {
        this.kind = kind;
        this.wind = wind;
    }

    public static WindUpdate absent() {
        return new WindUpdate(Kind.ABSENT, null);
    }

    public static WindUpdate clear() {
        return new WindUpdate(Kind.CLEAR, null);
    }

    public static WindUpdate set(UniformWind wind) {
        if (wind == null) {
            throw new IllegalArgumentException("wind must not be null for SET");
        }
        return new WindUpdate(Kind.SET, wind);
    }

    public Kind kind() {
        return kind;
    }

    public UniformWind wind() {
        return wind;
    }
}
