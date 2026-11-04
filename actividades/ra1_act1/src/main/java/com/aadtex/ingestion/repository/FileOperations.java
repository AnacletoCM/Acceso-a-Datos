package com.aadtex.ingestion.repository;

import com.aadtex.ingestion.config.IngestionProperties;
import com.aadtex.ingestion.io.LimitedInputStream;

import java.io.*;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class FileOperations {
    private static final Logger log = LoggerFactory.getLogger(FileOperations.class);
    static final int BUFFER_SIZE = 4096;
    static final long MAX_FILE_BYTES = LimitedInputStream.MAX_BYTES;
    private FileOperations() { }

    static void initialize(IngestionProperties p) throws IOException {
        Path requested = p.getBaseDir();
        try {
            createDirectories(p);
        } catch (IOException | SecurityException original) {
            if (!p.useLocalFallback()) throw original;
            try {
                createDirectories(p);
            } catch (IOException | SecurityException fallback) {
                fallback.addSuppressed(original);
                throw fallback;
            }
            log.warn("No se puede usar {}. Directorio de datos: {}", requested, p.getBaseDir());
        }
    }

    private static void createDirectories(IngestionProperties p) throws IOException {
        for (Path dir : new Path[]{p.input(), p.processed(), p.signatures(), p.error(), p.bucket(),
                p.getBaseDir().resolve("keys")}) {
            if (!Files.exists(dir)) Files.createDirectories(dir);
            if (!Files.isDirectory(dir)) throw new NotDirectoryException(dir.toString());
            Path probe = Files.createTempFile(dir, ".aadtex-write-", ".tmp");
            Files.delete(probe);
        }
    }

    static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // La imagen se valida por su cabecera y se copia sin cargarla entera en RAM.
    static void copyJpeg(InputStream source, Path target) throws IOException {
        boolean created = false;
        try (var in = new BufferedInputStream(new NonClosingInputStream(source), BUFFER_SIZE);
             var out = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW), BUFFER_SIZE)) {
            created = true;
            byte[] buffer = new byte[4096];
            int count = in.readNBytes(buffer, 0, 3);
            if (count != 3 || (buffer[0] & 255) != 255 || (buffer[1] & 255) != 216 || (buffer[2] & 255) != 255)
                throw new IOException("Cabecera JPEG inválida");
            out.write(buffer, 0, count);
            long total = count;
            int previous = buffer[1] & 255, last = buffer[2] & 255;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) throw new IOException("Firma mayor de 16 MiB");
                for (int i = 0; i < count; i++) { previous = last; last = buffer[i] & 255; }
                out.write(buffer, 0, count);
            }
            if (previous != 255 || last != 217) throw new IOException("JPEG truncado: falta FF D9");
        } catch (IOException | RuntimeException failure) {
            if (created) {
                try { Files.deleteIfExists(target); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    static class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) { super(in); }
        @Override public void close() { /* El propietario del ZIP cierra el contenedor. */ }
    }

}
