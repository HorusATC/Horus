package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.AtisService;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto.UpdateAtisRequestDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/atis")
public class AtisController {

    private final AtisService atisService;

    public AtisController(AtisService atisService) {
        this.atisService = atisService;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllAtis() {
        return ResponseEntity.ok(atisService.getAllAtis());
    }

    @PutMapping("/{airportIcao}")
    public ResponseEntity<?> updateAtis(
            @PathVariable String airportIcao,
            @Valid @RequestBody UpdateAtisRequestDto request) {
        try {
            Character ident = null;
            if (request.getInformationIdent() != null && !request.getInformationIdent().isBlank()) {
                ident = request.getInformationIdent().charAt(0);
            }
            return ResponseEntity.ok(atisService.updateAtis(
                    airportIcao,
                    request.getRunwayInUse(),
                    ident,
                    request.resolveWindUpdate()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }
}
