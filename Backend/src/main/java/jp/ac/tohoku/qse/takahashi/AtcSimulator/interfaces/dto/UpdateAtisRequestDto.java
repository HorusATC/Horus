package jp.ac.tohoku.qse.takahashi.AtcSimulator.interfaces.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.QnhUpdate;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.application.WindUpdate;
import jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Atmosphere.UniformWind;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateAtisRequestDto {

    @NotBlank
    private String runwayInUse;

    @Size(min = 1, max = 1)
    @Pattern(regexp = "[A-Za-z]", message = "informationIdent must be a single letter A-Z")
    private String informationIdent;

    /** {@code true} when the JSON body included a {@code wind} key (value may be null). */
    private boolean windPresent;

    @Valid
    private WindDto wind;

    /** {@code true} when the JSON body included a {@code qnhHpa} key (value may be null). */
    private boolean qnhPresent;

    private Integer qnhHpa;

    public String getRunwayInUse() {
        return runwayInUse;
    }

    public void setRunwayInUse(String runwayInUse) {
        this.runwayInUse = runwayInUse;
    }

    public String getInformationIdent() {
        return informationIdent;
    }

    public void setInformationIdent(String informationIdent) {
        this.informationIdent = informationIdent;
    }

    @JsonIgnore
    public boolean isWindPresent() {
        return windPresent;
    }

    public WindDto getWind() {
        return wind;
    }

    @JsonProperty("wind")
    public void setWind(WindDto wind) {
        this.windPresent = true;
        this.wind = wind;
    }

    @JsonIgnore
    public boolean isQnhPresent() {
        return qnhPresent;
    }

    public Integer getQnhHpa() {
        return qnhHpa;
    }

    @JsonProperty("qnhHpa")
    public void setQnhHpa(Integer qnhHpa) {
        this.qnhPresent = true;
        this.qnhHpa = qnhHpa;
    }

    public WindUpdate resolveWindUpdate() {
        if (!windPresent) {
            return WindUpdate.absent();
        }
        if (wind == null) {
            return WindUpdate.clear();
        }
        Integer speedKt = wind.speedKt();
        if (speedKt == null) {
            throw new IllegalArgumentException("wind.speedKt is required");
        }
        if (speedKt == 0) {
            return WindUpdate.set(UniformWind.calm());
        }
        Integer directionDeg = wind.directionDeg();
        if (directionDeg == null) {
            throw new IllegalArgumentException("wind.directionDeg is required when speedKt > 0");
        }
        return WindUpdate.set(UniformWind.measured(directionDeg, speedKt));
    }

    public QnhUpdate resolveQnhUpdate() {
        if (!qnhPresent) {
            return QnhUpdate.absent();
        }
        if (qnhHpa == null) {
            return QnhUpdate.clear();
        }
        return QnhUpdate.set(qnhHpa);
    }
}
