package com.medchart.ehr.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for export files.
 *
 * Output format: [12-byte IV][ciphertext+tag]
 * The encryption key is configured via medchart.export.encryption-key (Base64-encoded 256-bit key).
 */
@Service
@Slf4j
public class ExportEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public ExportEncryptionService(
            @Value("${medchart.export.encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 256 bits (32 bytes)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] output = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, output, GCM_IV_LENGTH, ciphertext.length);

            return output;
        } catch (Exception e) {
            throw new ExportException("Failed to encrypt export data", e);
        }
    }

    public byte[] decrypt(byte[] encrypted) {
        try {
            byte[] iv = Arrays.copyOfRange(encrypted, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_IV_LENGTH, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new ExportException("Failed to decrypt export data", e);
        }
    }
}
