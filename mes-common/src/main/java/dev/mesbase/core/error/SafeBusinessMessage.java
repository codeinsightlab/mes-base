package dev.mesbase.core.error;

/** Explicitly reviewed, user-facing business text. Never construct from exception messages. */
public record SafeBusinessMessage(String value) {
    private static final int MAX_LENGTH = 300;

    public SafeBusinessMessage {
        if (value == null || value.isBlank() || value.length() > MAX_LENGTH
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid safe business message");
        }
    }
}
