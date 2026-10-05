package dev.mesbase.core.error;
public class FoundationException extends RuntimeException {
    private final ErrorCode code;
    public FoundationException(ErrorCode code) { this(code, null); }
    public FoundationException(ErrorCode code, Throwable cause) { super(code.safeMessage(), cause); this.code=code; }
    public ErrorCode code() { return code; }
}
