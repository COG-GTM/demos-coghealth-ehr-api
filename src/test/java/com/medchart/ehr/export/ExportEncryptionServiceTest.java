package com.medchart.ehr.export;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ExportEncryptionServiceTest {

    private ExportEncryptionService service;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i + 1);
        }
        String base64Key = Base64.getEncoder().encodeToString(key);
        service = new ExportEncryptionService(base64Key);
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String original = "Patient data: MRN001, John Doe, DOB 1990-01-01";
        byte[] plaintext = original.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = service.encrypt(plaintext);
        byte[] decrypted = service.decrypt(encrypted);

        assertEquals(original, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void encrypt_outputIncludesIvPrefix() {
        byte[] plaintext = "test data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = service.encrypt(plaintext);

        // GCM: 12-byte IV + ciphertext + 16-byte tag
        assertTrue(encrypted.length > 12 + 16);
    }

    @Test
    void encrypt_producesUniqueOutputsPerCall() {
        byte[] plaintext = "same input".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted1 = service.encrypt(plaintext);
        byte[] encrypted2 = service.encrypt(plaintext);

        assertNotEquals(
            Base64.getEncoder().encodeToString(encrypted1),
            Base64.getEncoder().encodeToString(encrypted2),
            "Each encryption should use a unique IV"
        );
    }

    @Test
    void encrypt_handlesEmptyInput() {
        byte[] encrypted = service.encrypt(new byte[0]);
        byte[] decrypted = service.decrypt(encrypted);

        assertEquals(0, decrypted.length);
    }

    @Test
    void encrypt_handlesLargeInput() {
        byte[] largePlaintext = new byte[10_000_000];
        for (int i = 0; i < largePlaintext.length; i++) {
            largePlaintext[i] = (byte) (i % 256);
        }

        byte[] encrypted = service.encrypt(largePlaintext);
        byte[] decrypted = service.decrypt(encrypted);

        assertArrayEquals(largePlaintext, decrypted);
    }

    @Test
    void constructor_rejectsInvalidKeyLength() {
        byte[] shortKey = new byte[16];
        String base64Key = Base64.getEncoder().encodeToString(shortKey);

        assertThrows(IllegalArgumentException.class, () ->
            new ExportEncryptionService(base64Key));
    }
}
