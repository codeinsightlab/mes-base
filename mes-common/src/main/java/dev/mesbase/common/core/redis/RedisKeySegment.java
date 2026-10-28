package dev.mesbase.common.core.redis;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.io.ByteArrayOutputStream;

/** Readable, reversible encoding for one Redis key segment. */
public final class RedisKeySegment {
    private RedisKeySegment() {
    }

    public static String encode(String value) {
        if (value == null) throw new IllegalArgumentException("Redis key segment required");
        StringBuilder result = new StringBuilder();
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            int current = item & 0xff;
            if (current >= 'a' && current <= 'z' || current >= 'A' && current <= 'Z'
                    || current >= '0' && current <= '9' || current == '-' || current == '_' || current == '.') {
                result.append((char) current);
            } else {
                result.append('%');
                result.append(Character.toUpperCase(Character.forDigit(current >>> 4, 16)));
                result.append(Character.toUpperCase(Character.forDigit(current & 15, 16)));
            }
        }
        return result.toString();
    }

    public static String decode(String value) {
        if (value == null) throw new IllegalArgumentException("Redis key segment required");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int index = 0; index < value.length();) {
            char current = value.charAt(index);
            if (current == '%') {
                if (index + 2 >= value.length()) throw new IllegalArgumentException("Invalid Redis key escape");
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid Redis key escape");
                if (Character.toUpperCase(value.charAt(index + 1)) != value.charAt(index + 1)
                        || Character.toUpperCase(value.charAt(index + 2)) != value.charAt(index + 2))
                    throw new IllegalArgumentException("Noncanonical Redis key escape");
                int decoded = (high << 4) | low;
                if (decoded >= 'a' && decoded <= 'z' || decoded >= 'A' && decoded <= 'Z'
                        || decoded >= '0' && decoded <= '9' || decoded == '-' || decoded == '_' || decoded == '.')
                    throw new IllegalArgumentException("Noncanonical Redis key escape");
                bytes.write(decoded);
                index += 3;
            } else {
                if (!(current >= 'a' && current <= 'z' || current >= 'A' && current <= 'Z'
                        || current >= '0' && current <= '9' || current == '-' || current == '_' || current == '.'))
                    throw new IllegalArgumentException("Unescaped Redis key character");
                bytes.write((byte) current);
                index++;
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Invalid UTF-8 Redis key segment", failure);
        }
    }
}
