package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

/**
 * Optional per-airport QNH change on ATIS update: omitted field, clear to missing, or set hPa.
 */
public final class QnhUpdate {

    public static final int MIN_HPA = 850;
    public static final int MAX_HPA = 1100;

    public enum Kind {
        ABSENT,
        CLEAR,
        SET
    }

    private final Kind kind;
    private final Integer qnhHpa;

    private QnhUpdate(Kind kind, Integer qnhHpa) {
        this.kind = kind;
        this.qnhHpa = qnhHpa;
    }

    public static QnhUpdate absent() {
        return new QnhUpdate(Kind.ABSENT, null);
    }

    public static QnhUpdate clear() {
        return new QnhUpdate(Kind.CLEAR, null);
    }

    public static QnhUpdate set(int qnhHpa) {
        if (qnhHpa < MIN_HPA || qnhHpa > MAX_HPA) {
            throw new IllegalArgumentException(
                    "qnhHpa must be " + MIN_HPA + ".." + MAX_HPA + ", got " + qnhHpa);
        }
        return new QnhUpdate(Kind.SET, qnhHpa);
    }

    public Kind kind() {
        return kind;
    }

    public Integer qnhHpa() {
        return qnhHpa;
    }
}
