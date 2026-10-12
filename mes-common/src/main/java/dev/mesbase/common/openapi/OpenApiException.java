package dev.mesbase.common.openapi;


public class OpenApiException extends RuntimeException {
    private final OpenApiError error;

    public OpenApiException(OpenApiError error) {
        super(error.getName());
        this.error = error;
    }

    public OpenApiException(OpenApiError error, Throwable cause) {
        super(error.getName(), cause);
        this.error = error;
    }

    public OpenApiError getError() {
        return error;
    }
}
