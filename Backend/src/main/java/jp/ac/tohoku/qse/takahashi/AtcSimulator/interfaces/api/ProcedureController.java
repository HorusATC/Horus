package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.InstrumentProcedure;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureAltitude;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.entity.procedure.ProcedureLeg;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.infrastructure.fix.ProcedureCatalog;

@RestController
@RequestMapping("/api/procedures")
public class ProcedureController {

    private final ProcedureCatalog procedureCatalog;

    public ProcedureController(ProcedureCatalog procedureCatalog) {
        this.procedureCatalog = procedureCatalog;
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<Map<String, Object>> listProcedures(
            @RequestParam(value = "airport", required = false) String airport) {
        List<InstrumentProcedure> procedures = airport == null || airport.isBlank()
                ? procedureCatalog.all()
                : procedureCatalog.listByAirport(airport);

        List<Map<String, Object>> items = new ArrayList<>();
        for (InstrumentProcedure procedure : procedures) {
            items.add(toSummary(procedure));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceNotice", procedureCatalog.getSourceNotice());
        body.put("procedures", items);
        return ResponseEntity.ok(body);
    }

    @GetMapping(path = "/{id}", produces = "application/json")
    public ResponseEntity<Map<String, Object>> getProcedure(@PathVariable String id) {
        return procedureCatalog.findById(id)
                .map(procedure -> ResponseEntity.ok(toDetail(procedure)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> toSummary(InstrumentProcedure procedure) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", procedure.id());
        row.put("type", procedure.type().name());
        row.put("name", procedure.name());
        row.put("family", procedure.family());
        row.put("variant", procedure.variant());
        row.put("airportIcao", procedure.airportIcao());
        row.put("rnav", procedure.rnav());
        row.put("assignable", procedure.isAssignablePrimary());
        row.put("applicableRunways", procedure.applicableRunways());
        row.put("enrouteTransitions", procedure.enrouteTransitions());
        row.put("legCount", procedure.legs().size());
        return row;
    }

    private Map<String, Object> toDetail(InstrumentProcedure procedure) {
        Map<String, Object> body = toSummary(procedure);
        body.put("sourceNotice", procedureCatalog.getSourceNotice());
        List<Map<String, Object>> legs = new ArrayList<>();
        for (ProcedureLeg leg : procedure.legs()) {
            legs.add(toLeg(leg));
        }
        body.put("legs", legs);
        return body;
    }

    private static Map<String, Object> toLeg(ProcedureLeg leg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("serial", leg.serial());
        row.put("pathDescriptor", leg.pathDescriptor().name());
        if (leg.waypointIdentifier() != null) {
            row.put("waypointIdentifier", leg.waypointIdentifier());
        }
        row.put("flyOver", leg.flyOver());
        if (leg.courseMagneticDeg() != null) {
            row.put("courseMagneticDeg", leg.courseMagneticDeg());
        }
        if (leg.courseTrueDeg() != null) {
            row.put("courseTrueDeg", leg.courseTrueDeg());
        }
        if (leg.distanceNm() != null) {
            row.put("distanceNm", leg.distanceNm());
        }
        if (leg.turnDirection() != null) {
            row.put("turnDirection", leg.turnDirection());
        }
        if (leg.altitude() != null) {
            row.put("altitude", toAltitude(leg.altitude()));
        }
        if (leg.speedKias() != null) {
            row.put("speedKias", leg.speedKias());
        }
        return row;
    }

    private static Map<String, Object> toAltitude(ProcedureAltitude altitude) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("constraint", altitude.constraint().name());
        if (altitude.feet() != null) {
            row.put("ft", altitude.feet());
        }
        if (altitude.flightLevel() != null) {
            row.put("fl", altitude.flightLevel());
        }
        Integer mapped = altitude.resolveFeetForFlightPlan();
        if (mapped != null) {
            row.put("mappedFeet", mapped);
        }
        return row;
    }
}
