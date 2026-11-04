package com.aadtex.ingestion.service;

import com.aadtex.ingestion.io.LimitedInputStream;
import com.aadtex.ingestion.model.SalesSummary;
import com.aadtex.ingestion.repository.StoreFileRepository;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class StoreIngestionService {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private final StoreFileRepository files;
    private final SalesService salesService;
    private final CryptoService crypto;
    private final CloudBucketService bucket;

    public StoreIngestionService(StoreFileRepository files, SalesService salesService,
            CryptoService crypto, CloudBucketService bucket) {
        this.files = files; this.salesService = salesService; this.crypto = crypto; this.bucket = bucket;
    }

    public synchronized void processPending() throws IOException {
        files.initialize();
        try (var lock = files.tryAcquireLock()) {
            if (lock == null) return;
            List<Path> pending = files.findPendingFiles().stream()
                    .sorted(Comparator.comparingInt((Path path) -> extension(path).equals("jpg") ? 1 : 0)
                            .thenComparing(path -> path.getFileName().toString())).toList();
            for (Path source : pending) {
                if (!files.exists(source)) continue;
                String ext = extension(source);
                if (!Set.of("csv", "enc", "zip", "jpg").contains(ext)) continue;
                Path pair = null;
                try {
                    if (ext.equals("csv") || ext.equals("enc"))
                        pair = files.inputFile(expectedSignature(source.getFileName().toString()));
                    if (files.size(source) == 0) throw new IOException("Fichero vacío");
                    if (ext.equals("jpg")) copyStandalone(source);
                    else processBatch(source, pair, ext);
                } catch (Exception e) {
                    log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {}", source.getFileName());
                    log.error("Motivo: {}", e.getMessage());
                    quarantine(source);
                    if (pair != null) quarantine(pair);
                }
            }
        }
    }

    private void processBatch(Path source, Path pair, String extension) throws Exception {
        String id = UUID.randomUUID().toString();
        Path archive = files.archiveDirectory(id);
        Path copiedSignature = null;
        List<Path> moved = new ArrayList<>();
        try {
            SalesSummary sales;
            if (extension.equals("zip")) {
                ZipSales zipped = readZip(source, id);
                sales = zipped.sales();
                copiedSignature = zipped.signature();
            } else {
                if (!files.isRegularFile(pair)) throw new IOException("Falta la firma: " + pair.getFileName());
                if (extension.equals("enc")) {
                    byte[] plaintext = crypto.decrypt(source);
                    try (var input = new ByteArrayInputStream(plaintext)) { sales = salesService.summarize(input); }
                    finally { Arrays.fill(plaintext, (byte) 0); }
                } else {
                    try (var input = files.openInput(source)) { sales = salesService.summarize(input); }
                }
                copiedSignature = files.copySignature(pair, id);
            }
            files.createArchive(archive);
            files.archive(source, archive);
            moved.add(source);
            if (pair != null) {
                files.archive(pair, archive);
                moved.add(pair);
            }
            bucket.publish(id, source.getFileName().toString(), sales, copiedSignature.getFileName().toString());
            log.info("PROCESSED {} | ventas={} | total={} | tiendas={}", source.getFileName(),
                    sales.transactions(), sales.totalSales(), sales.storeNames());
        } catch (Exception failure) {
            if (copiedSignature != null) removeCopy(copiedSignature, failure);
            for (Path original : moved) {
                try { files.restore(archive, original); }
                catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { files.deleteIfExists(archive); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private record ZipSales(SalesSummary sales, Path signature) { }

    private ZipSales readZip(Path source, String id) throws IOException {
        if (files.zipEntryCount(source) != 2) throw new IOException("El ZIP debe contener exactamente dos entradas");
        var batch = new ZipBatch(id);
        try {
            files.readZip(source, batch::accept);
            if (batch.sales == null || batch.signature == null || !expectedSignature(batch.csvName).equals(batch.signatureName))
                throw new IOException("El ZIP necesita un CSV y su firma correspondiente");
            return new ZipSales(batch.sales, batch.signature);
        } catch (IOException | RuntimeException failure) {
            if (batch.signature != null) removeCopy(batch.signature, failure);
            throw failure;
        }
    }

    private final class ZipBatch {
        private final String id;
        private final Set<String> names = new HashSet<>();
        private SalesSummary sales;
        private String csvName, signatureName;
        private Path signature;

        private ZipBatch(String id) { this.id = id; }

        private void accept(ZipEntry entry, InputStream input) throws IOException {
            String name = entry.getName();
            if (entry.isDirectory() || name.contains("/") || name.contains("\\") || name.contains(":")
                    || name.equals("..") || !names.add(name) || names.size() > 2)
                throw new IOException("Entrada ZIP no permitida: " + name);
            if (entry.getSize() > LimitedInputStream.MAX_BYTES) throw new IOException("Entrada ZIP demasiado grande");
            if (name.startsWith("daily_sales_") && name.endsWith(".csv") && sales == null) {
                csvName = name;
                sales = salesService.summarize(input);
            } else if (name.startsWith("signature_") && name.endsWith(".jpg") && signature == null) {
                signatureName = name;
                signature = files.copySignature(input, name, id);
            } else throw new IOException("Contenido ZIP inesperado: " + name);
        }
    }

    private void copyStandalone(Path source) throws IOException {
        Path target = files.copySignature(source, UUID.randomUUID().toString());
        try {
            files.deleteIfExists(source);
            log.info("Firma copiada: {}", target.getFileName());
        } catch (IOException failure) { removeCopy(target, failure); throw failure; }
    }

    private void removeCopy(Path target, Exception failure) {
        try { files.deleteIfExists(target); }
        catch (IOException cleanup) { failure.addSuppressed(cleanup); }
    }

    private String expectedSignature(String salesName) throws IOException {
        if (!salesName.startsWith("daily_sales_")) throw new IOException("Nombre de lote no válido: " + salesName);
        int end = salesName.lastIndexOf('.');
        return "signature_" + salesName.substring("daily_sales_".length(), end) + ".jpg";
    }

    private void quarantine(Path source) {
        try { files.quarantine(source); }
        catch (IOException e) {
            log.error("No se pudo aislar {}: {}. Se reintentará en el siguiente ciclo.", source.getFileName(), e.getMessage());
        }
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
