package dev.mesbase.business.integration.warehouse;

public record ErpActionResult(Status status, Mode executionMode, String externalReference, String externalCode, String message) {
    public ErpActionResult(Status status, Mode executionMode, String externalReference, String message) {
        this(status, executionMode, externalReference, null, message);
    }
    public enum Status {SUCCEEDED, REJECTED, UNKNOWN}

    public enum Mode {LOCAL_ONLY, CONNECTED}

    public static ErpActionResult localSuccess() {
        return new ErpActionResult(Status.SUCCEEDED, Mode.LOCAL_ONLY, null, "仅完成MES本地业务，无ERP外部副作用");
    }
}
