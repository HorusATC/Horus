package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.flightplan;

/**
 * Parameters for a hold-at-fix instruction after API validation.
 *
 * @param inboundCourseDeg null means use the historical fix-name hash inbound
 */
public record HoldInstruction(
        String fixName,
        HoldTurnDirection turnDirection,
        Double inboundCourseDeg,
        HoldLeg leg
) {}
