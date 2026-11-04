package com.aadtex.ingestion.service;

import com.aadtex.ingestion.repository.StoreFileRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    public static final String DEMO_KEY = "AadTexSecret2026";
    private final StoreFileRepository files;
    public CryptoService(StoreFileRepository files) { this.files = files; }

    public byte[] decrypt(Path encrypted) throws IOException, GeneralSecurityException {
        byte[] key = files.readSecretKey();
        byte[] ciphertext = null;
        try {
            if (key.length != 16) throw new IOException("AES-128 exige exactamente 16 bytes de clave");
            ciphertext = files.readEncrypted(encrypted);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(ciphertext);
        } finally {
            Arrays.fill(key, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
        }
    }
}
