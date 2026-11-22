package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAtisRequestDto(
        @NotBlank String runwayInUse,
        @Size(min = 1, max = 1)
                @Pattern(regexp = "[A-Za-z]", message = "informationIdent must be a single letter A-Z")
                String informationIdent) {
}
