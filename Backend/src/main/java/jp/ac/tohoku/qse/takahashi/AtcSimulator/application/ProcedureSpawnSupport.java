package jp.ac.tohoku.qse.takahashi.AtcSimulator.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.aircraft.AircraftBase;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Callsign.Callsign;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.FlightPlanDto;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.SpawnWithFlightPlanDto;

/**
 * Applies optional {@code procedureId} / {@code transitionId} on spawn the same way as
 * {@code POST /api/aircraft/{callsign}/assign-procedure}.
 */
@Component
public class ProcedureSpawnSupport {

    public enum ResolveKind {
        NONE,
        OK,
        NOT_FOUND,
        BAD_REQUEST
    }

    public record ResolveResult(
            ResolveKind kind,
            String message,
            ProcedureFlightPlanMapper.MappedProcedureRoute mapped
    ) {
        public static ResolveResult none() {
            return new ResolveResult(ResolveKind.NONE, null, null);
        }

        public static ResolveResult ok(ProcedureFlightPlanMapper.MappedProcedureRoute mapped) {
            return new ResolveResult(ResolveKind.OK, null, mapped);
        }

        public static ResolveResult notFound(String message) {
            return new ResolveResult(ResolveKind.NOT_FOUND, message, null);
        }

        public static ResolveResult badRequest(String message) {
            return new ResolveResult(ResolveKind.BAD_REQUEST, message, null);
        }
    }

    private final ProcedureCatalog procedureCatalog;
    private final ProcedureFlightPlanMapper procedureFlightPlanMapper;

    public ProcedureSpawnSupport(
            ProcedureCatalog procedureCatalog, ProcedureFlightPlanMapper procedureFlightPlanMapper) {
        this.procedureCatalog = procedureCatalog;
        this.procedureFlightPlanMapper = procedureFlightPlanMapper;
    }

    public ResolveResult resolve(SpawnWithFlightPlanDto dto) {
        if (dto.procedureId() == null || dto.procedureId().isBlank()) {
            return ResolveResult.none();
        }
        var primaryOpt = procedureCatalog.findById(dto.procedureId());
        if (primaryOpt.isEmpty() || !primaryOpt.get().isAssignablePrimary()) {
            return ResolveResult.notFound("Unknown or unassignable procedure: " + dto.procedureId());
        }
        InstrumentProcedure primary = primaryOpt.get();
        Optional<InstrumentProcedure> transition = Optional.empty();
        if (dto.transitionId() != null && !dto.transitionId().isBlank()) {
            var transitionOpt = procedureCatalog.findById(dto.transitionId().trim());
            if (transitionOpt.isEmpty()) {
                return ResolveResult.notFound("Unknown transition: " + dto.transitionId());
            }
            transition = transitionOpt;
        }

        FlightPlanDto fp = dto.flightPlan();
        try {
            ProcedureFlightPlanMapper.MappedProcedureRoute mapped = procedureFlightPlanMapper.map(
                    new Callsign(fp.callsign()),
                    primary,
                    transition,
                    fp.cruiseAltitude(),
                    fp.cruiseSpeed());
            return ResolveResult.ok(mapped);
        } catch (IllegalArgumentException ex) {
            return ResolveResult.badRequest(ex.getMessage());
        }
    }

    public void apply(AircraftBase aircraft, ProcedureFlightPlanMapper.MappedProcedureRoute mapped) {
        aircraft.assignProcedureRoute(mapped.flightPlan(), mapped.vaConstraint());
    }
}
