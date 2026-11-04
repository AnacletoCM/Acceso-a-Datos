package com.aadtex.ingestion.repository;

import com.aadtex.ingestion.config.IngestionProperties;
import com.aadtex.ingestion.io.LimitedInputStream;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.zip.*;
import org.springframework.stereotype.Repository;

@Repository
public class StoreFileRepository {
    private final IngestionProperties properties;

    public StoreFileRepository(IngestionProperties properties) { this.properties = properties; }

    public void initialize() throws IOException { FileOperations.initialize(properties); }
    public Path inputDirectory() { return properties.input(); }
    public Path inputFile(String name) { return properties.input().resolve(name); }
    public Path archiveDirectory(String id) { return properties.processed().resolve("batch_" + id); }
    public boolean exists(Path path) { return Files.exists(path, LinkOption.NOFOLLOW_LINKS); }
    public boolean isRegularFile(Path path) { return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS); }
    public long size(Path path) throws IOException { return Files.size(path); }
    public InputStream openInput(Path path) throws IOException { return Files.newInputStream(path); }

    public List<Path> findPendingFiles() throws IOException {
        try (var paths = Files.list(properties.input())) {
            return paths.filter(this::isRegularFile).toList();
        }
    }

    public BatchLock tryAcquireLock() throws IOException {
        FileChannel channel = FileChannel.open(properties.getBaseDir().resolve(".ingestion.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { channel.close(); return null; }
            if (lock == null) { channel.close(); return null; }
            return new BatchLock(channel, lock);
        } catch (IOException | RuntimeException e) {
            try { channel.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
    }

    public static final class BatchLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private BatchLock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        @Override public void close() throws IOException {
            try (channel) { lock.release(); }
        }
    }

    public int zipEntryCount(Path source) throws IOException {
        try (var zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) { return zip.size(); }
    }

    @FunctionalInterface
    public interface ZipEntryConsumer { void accept(ZipEntry entry, InputStream input) throws IOException; }

    public void readZip(Path source, ZipEntryConsumer consumer) throws IOException {
        try (var zip = new ZipInputStream(new BufferedInputStream(openInput(source), 4096), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                consumer.accept(entry, zip);
                zip.closeEntry();
            }
        }
    }

    public byte[] readSecretKey() throws IOException {
        Path key = properties.getBaseDir().resolve("keys/AadTexSecret.key");
        if (Files.size(key) > 128) throw new IOException("Fichero de clave inválido");
        return Files.readString(key, StandardCharsets.UTF_8).strip().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] readEncrypted(Path source) throws IOException {
        try (var input = new LimitedInputStream(openInput(source), LimitedInputStream.MAX_BYTES)) {
            return input.readAllBytes();
        }
    }

    public Path copySignature(Path source, String id) throws IOException {
        try (var input = openInput(source)) { return copySignature(input, source.getFileName().toString(), id); }
    }

    public Path copySignature(InputStream source, String name, String id) throws IOException {
        Path target = properties.signatures().resolve(name.substring(0, name.length() - 4) + "_" + id + ".jpg");
        FileOperations.copyJpeg(source, target);
        return target;
    }

    public void createArchive(Path directory) throws IOException { Files.createDirectories(directory); }
    public void archive(Path source, Path directory) throws IOException {
        FileOperations.move(source, directory.resolve(source.getFileName()));
    }
    public void restore(Path directory, Path original) throws IOException {
        FileOperations.move(directory.resolve(original.getFileName()), original);
    }
    public void deleteIfExists(Path path) throws IOException { Files.deleteIfExists(path); }

    public void quarantine(Path source) throws IOException {
        if (source == null || !isRegularFile(source)) return;
        Path target = properties.error().resolve(source.getFileName());
        if (Files.exists(target)) target = properties.error().resolve(UUID.randomUUID() + "_" + source.getFileName());
        FileOperations.move(source, target);
    }
}
