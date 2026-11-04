package com.aadtex.ingestion.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public final class LimitedInputStream extends FilterInputStream {
    public static final long MAX_BYTES = 16L * 1024 * 1024;
    private long remaining;

    public LimitedInputStream(InputStream input, long limit) {
        super(input);
        if (limit < 0) throw new IllegalArgumentException("El límite no puede ser negativo");
        remaining = limit;
    }

    @Override public int read() throws IOException {
        int value = in.read();
        if (value != -1 && --remaining < 0) throw new IOException("Límite de lectura excedido");
        return value;
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) return 0;
        int count = in.read(buffer, offset, (int) Math.min(length, remaining + 1));
        if (count > 0 && (remaining -= count) < 0) throw new IOException("Límite de lectura excedido");
        return count;
    }
}
