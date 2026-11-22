package jp.ac.tohoku.qse.takahashi.AtcSimulator.domain.model.valueObject.Msaw;

public record MsawAlert(
    MsawAlertType type,
    double msaFt,
    MsawTrigger trigger
) {}
