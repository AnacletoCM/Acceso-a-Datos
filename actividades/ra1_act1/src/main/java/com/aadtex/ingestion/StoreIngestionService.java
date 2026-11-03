package com.aadtex.ingestion;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class StoreIngestionService {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionService.class);
    private final IngestionProperties p;
    private final CsvSalesReader csv;
    private final CryptoService crypto;
    private final CloudBucketService bucket;

    public StoreIngestionService(IngestionProperties p, CsvSalesReader csv, CryptoService crypto, CloudBucketService bucket) {
        this.p = p; this.csv = csv; this.crypto = crypto; this.bucket = bucket;
    }

    public synchronized void processPending() throws IOException {
        FileOperations.initialize(p);
        // Dos aplicaciones sobre el mismo directorio no deben consumir un mismo lote.
        try (var channel = FileChannel.open(p.getBaseDir().resolve(".ingestion.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { return; }
            if (lock == null) return;
            try (lock; var files = Files.list(p.input())) {
                List<Path> pending = files.filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                        .sorted(Comparator.comparingInt((Path f) -> extension(f).equals("jpg") ? 1 : 0)
                                .thenComparing(f -> f.getFileName().toString())).toList();
                for (Path source : pending) {
                    if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
                    String ext = extension(source);
                    if (!Set.of("csv", "enc", "zip", "jpg").contains(ext)) continue;
                    Path pair = null;
                    try {
                        if (ext.equals("csv") || ext.equals("enc")) pair = signatureFor(source);
                        if (Files.size(source) == 0) throw new IOException("Fichero vacío");
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
    }

    private void processBatch(Path source, Path pair, String ext) throws Exception {
        String id = UUID.randomUUID().toString();
        Path archive = p.processed().resolve("batch_" + id);
        Path copiedSignature = null;
        List<Path> moved = new ArrayList<>();
        try {
            CsvSalesReader.Sales sales;
            if (ext.equals("zip")) {
                ZipSales zipped = readZip(source, id);
                sales = zipped.sales();
                copiedSignature = zipped.signature();
            } else {
                if (!Files.isRegularFile(pair, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Falta la firma: " + pair.getFileName());
                if (ext.equals("enc")) {
                    byte[] plaintext = crypto.decrypt(source);
                    try (var in = new ByteArrayInputStream(plaintext)) { sales = csv.read(in); }
                    finally { Arrays.fill(plaintext, (byte) 0); }
                } else {
                    try (var in = Files.newInputStream(source)) { sales = csv.read(in); }
                }
                copiedSignature = signatureOutput(pair.getFileName().toString(), id);
                try (var in = Files.newInputStream(pair)) { FileOperations.copyJpeg(in, copiedSignature); }
            }
            Files.createDirectories(archive);
            FileOperations.move(source, archive.resolve(source.getFileName()));
            moved.add(source);
            if (pair != null) {
                FileOperations.move(pair, archive.resolve(pair.getFileName()));
                moved.add(pair);
            }
            // El JSON aparece solo cuando la validación y el archivado han terminado.
            bucket.publish(id, source.getFileName().toString(), sales, copiedSignature.getFileName().toString());
            log.info("PROCESSED {} | ventas={} | total={} | tiendas={}", source.getFileName(),
                    sales.transactions(), sales.totalSales(), sales.storeNames());
        } catch (Exception e) {
            if (copiedSignature != null) Files.deleteIfExists(copiedSignature);
            for (Path original : moved) FileOperations.move(archive.resolve(original.getFileName()), original);
            Files.deleteIfExists(archive);
            throw e;
        }
    }

    private record ZipSales(CsvSalesReader.Sales sales, Path signature) { }

    private ZipSales readZip(Path source, String id) throws IOException {
        // ZipInputStream no exige directorio central: esta comprobación detecta ZIP truncados.
        // ZipFile solo inspecciona el contenedor; los datos se consumen después en caliente.
        try (var index = new ZipFile(source.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
            if (index.size() != 2) throw new IOException("El ZIP debe contener exactamente dos entradas");
        }
        CsvSalesReader.Sales sales = null;
        String csvName = null, signatureName = null;
        Path signature = null;
        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(source), 4096), java.nio.charset.StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                // Solo se admiten los dos ficheros planos del contrato; evita Zip Slip.
                if (entry.isDirectory() || name.contains("/") || name.contains("\\") || name.contains(":")
                        || name.equals("..") || !names.add(name) || names.size() > 2)
                    throw new IOException("Entrada ZIP no permitida: " + name);
                if (entry.getSize() > FileOperations.MAX_FILE_BYTES) throw new IOException("Entrada ZIP demasiado grande");
                if (name.startsWith("daily_sales_") && name.endsWith(".csv") && sales == null) {
                    csvName = name;
                    sales = csv.read(zip);
                } else if (name.startsWith("signature_") && name.endsWith(".jpg") && signature == null) {
                    signatureName = name;
                    signature = signatureOutput(name, id);
                    FileOperations.copyJpeg(zip, signature);
                } else throw new IOException("Contenido ZIP inesperado: " + name);
                zip.closeEntry(); // También comprueba el CRC al agotar la entrada.
            }
            if (sales == null || signature == null || !expectedSignature(csvName).equals(signatureName))
                throw new IOException("El ZIP necesita un CSV y su firma correspondiente");
            return new ZipSales(sales, signature);
        } catch (IOException | RuntimeException e) {
            if (signature != null) Files.deleteIfExists(signature);
            throw e;
        }
    }

    private void copyStandalone(Path source) throws IOException {
        Path target = signatureOutput(source.getFileName().toString(), UUID.randomUUID().toString());
        try {
            try (var in = Files.newInputStream(source)) { FileOperations.copyJpeg(in, target); }
            Files.delete(source);
            log.info("Firma copiada: {}", target.getFileName());
        } catch (IOException e) { Files.deleteIfExists(target); throw e; }
    }

    private Path signatureOutput(String name, String id) {
        return p.signatures().resolve(name.substring(0, name.length() - 4) + "_" + id + ".jpg");
    }
    private Path signatureFor(Path source) throws IOException {
        return p.input().resolve(expectedSignature(source.getFileName().toString()));
    }
    private String expectedSignature(String salesName) throws IOException {
        if (!salesName.startsWith("daily_sales_")) throw new IOException("Nombre de lote no válido: " + salesName);
        int end = salesName.lastIndexOf('.');
        return "signature_" + salesName.substring("daily_sales_".length(), end) + ".jpg";
    }
    private void quarantine(Path source) {
        if (source == null || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Path target = p.error().resolve(source.getFileName());
            if (Files.exists(target)) target = p.error().resolve(UUID.randomUUID() + "_" + source.getFileName());
            FileOperations.move(source, target);
        } catch (IOException e) {
            log.error("No se pudo aislar {}: {}. Se reintentará en el siguiente ciclo.", source.getFileName(), e.getMessage());
        }
    }
    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
