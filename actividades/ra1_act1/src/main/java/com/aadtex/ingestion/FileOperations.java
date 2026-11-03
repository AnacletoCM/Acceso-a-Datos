package com.aadtex.ingestion;

import java.io.*;
import java.nio.file.*;

final class FileOperations {
    static final int BUFFER_SIZE = 4096;
    static final long MAX_FILE_BYTES = 16L * 1024 * 1024;
    private FileOperations() { }

    static void initialize(IngestionProperties p) throws IOException {
        for (Path dir : new Path[]{p.input(), p.processed(), p.signatures(), p.error(), p.bucket(),
                p.getBaseDir().resolve("keys")}) {
            if (!Files.exists(dir)) Files.createDirectories(dir);
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
        try (var in = new BufferedInputStream(new NonClosingInputStream(source), BUFFER_SIZE);
             var out = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW), BUFFER_SIZE)) {
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
        }
    }

    static class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) { super(in); }
        @Override public void close() { /* El propietario del ZIP cierra el contenedor. */ }
    }

    static class LimitedInputStream extends FilterInputStream {
        private long remaining;
        LimitedInputStream(InputStream in, long limit) { super(in); remaining = limit; }
        @Override public int read() throws IOException {
            int n = in.read();
            if (n != -1 && --remaining < 0) throw new IOException("Límite de lectura excedido");
            return n;
        }
        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, (int) Math.min(len, remaining + 1));
            if (n > 0 && (remaining -= n) < 0) throw new IOException("Límite de lectura excedido");
            return n;
        }
    }
}
