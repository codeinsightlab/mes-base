package dev.mesbase.server;

import dev.mesbase.server.managed.DatasourcePasswordCipher;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class P1CipherTest {
    @Test
    void randomRuntimeKeyKeepsExistingGcmFormatAndHasNoDebugEntrypoint() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        var cipher = new DatasourcePasswordCipher(Base64.getEncoder().encodeToString(bytes));
        String encoded = cipher.encrypt("SYNTHETIC TEST ONLY");
        assertEquals("SYNTHETIC TEST ONLY", cipher.decrypt(encoded));
        assertTrue(Base64.getDecoder().decode(encoded).length >= 28);
        assertNotEquals(encoded, cipher.encrypt("SYNTHETIC TEST ONLY"));
        assertFalse(Arrays.stream(DatasourcePasswordCipher.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("main")));
        new SecureRandom().nextBytes(bytes);
        var other = new DatasourcePasswordCipher(Base64.getEncoder().encodeToString(bytes));
        assertThrows(IllegalStateException.class, () -> other.decrypt(encoded));
    }
}
