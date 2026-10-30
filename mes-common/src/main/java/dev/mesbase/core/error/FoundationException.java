package dev.mesbase.core.error;
public class FoundationException extends RuntimeException {
    private final ErrorCode code;
    private final SafeBusinessMessage businessMessage;
    public FoundationException(ErrorCode code) { this(code, (Throwable) null); }
    public FoundationException(ErrorCode code, Throwable cause) { this(code, cause, null); }
    public FoundationException(ErrorCode code, SafeBusinessMessage businessMessage) { this(code, null, businessMessage); }
    private FoundationException(ErrorCode code, Throwable cause, SafeBusinessMessage businessMessage) {
        super(businessMessage == null ? code.safeMessage() : businessMessage.value(), cause);
        this.code = code;
        this.businessMessage = businessMessage;
    }
    public ErrorCode code() { return code; }
    public String safeMessage() { return businessMessage == null ? code.safeMessage() : businessMessage.value(); }
}
