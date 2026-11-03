package com.aadtex.ingestion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    public static final String DEMO_KEY = "AadTexSecret2026";
    private final IngestionProperties properties;
    public CryptoService(IngestionProperties properties) { this.properties = properties; }

    public byte[] decrypt(Path encrypted) throws IOException, GeneralSecurityException {
        Path keyPath = properties.getBaseDir().resolve("keys/AadTexSecret.key");
        if (Files.size(keyPath) > 128) throw new IOException("Fichero de clave inválido");
        byte[] key = Files.readString(keyPath, StandardCharsets.UTF_8).strip().getBytes(StandardCharsets.UTF_8);
        if (key.length != 16) throw new IOException("AES-128 exige exactamente 16 bytes de clave");
        byte[] ciphertext;
        try (var in = new FileOperations.LimitedInputStream(Files.newInputStream(encrypted), FileOperations.MAX_FILE_BYTES)) {
            ciphertext = in.readAllBytes();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(ciphertext);
        } finally { Arrays.fill(key, (byte) 0); Arrays.fill(ciphertext, (byte) 0); }
    }
}
