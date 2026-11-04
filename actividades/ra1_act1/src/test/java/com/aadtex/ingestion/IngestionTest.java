package com.aadtex.ingestion;

import com.aadtex.ingestion.bootstrap.MultiLanguageDataGenerator;
import com.aadtex.ingestion.config.IngestionProperties;
import com.aadtex.ingestion.io.LimitedInputStream;
import com.aadtex.ingestion.repository.CloudBucketRepository;
import com.aadtex.ingestion.repository.CsvSalesRepository;
import com.aadtex.ingestion.repository.DemoDataRepository;
import com.aadtex.ingestion.repository.StoreFileRepository;
import com.aadtex.ingestion.service.*;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.*;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class IngestionTest {
    @TempDir(factory = WorkspaceTempFactory.class) Path temp;
    public static class WorkspaceTempFactory implements TempDirFactory {
        @Override public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
            Path parent = Path.of("target", "test-data").toAbsolutePath();
            Files.createDirectories(parent);
            return Files.createTempDirectory(parent, "batch-");
        }
    }
    IngestionProperties p;
    StoreIngestionService service;
    StoreFileRepository files;
    DemoDataService demo;
    final SalesService sales = new SalesService(new CsvSalesRepository());

    @BeforeEach void setup() throws Exception {
        p = new IngestionProperties();
        p.setBaseDir(temp);
        files = new StoreFileRepository(p);
        files.initialize();
        demo = new DemoDataService(p, files, new DemoDataRepository(p));
        service = new StoreIngestionService(files, sales, new CryptoService(files),
                new CloudBucketService(new CloudBucketRepository(p)));
    }

    @Test void allProvidedBatchesAndRestart(CapturedOutput output) throws Exception {
        var generator = new MultiLanguageDataGenerator(demo);
        generator.run();
        byte[] originalSignature = Files.readAllBytes(p.input().resolve("signature_spain.jpg"));
        service.processPending();
        assertThat(list(p.input())).isEmpty();
        assertThat(list(p.bucket())).hasSize(4);
        assertThat(list(p.signatures())).hasSize(4);
        assertThat(list(p.error())).extracting(f -> f.getFileName().toString()).containsExactly("daily_sales_broken_store.csv");
        Map<String, String> summaries = new HashMap<>();
        for (Path path : list(p.bucket())) {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            for (String source : List.of("spain", "china", "financial", "usa")) if (json.contains(source)) summaries.put(source, json);
        }
        assertTotal(summaries.get("spain"), "20100.80");
        assertTotal(summaries.get("china"), "41300.80");
        assertTotal(summaries.get("financial"), "95000.00");
        assertTotal(summaries.get("usa"), "68450.75");
        assertThat(summaries.get("spain")).contains("Gran Vía");
        assertThat(summaries.get("china")).contains("上海南京东路店");
        Path signature = list(p.signatures()).stream().filter(f -> f.getFileName().toString().startsWith("signature_spain_")).findFirst().orElseThrow();
        assertThat(Files.readAllBytes(signature)).isEqualTo(originalSignature);
        assertThat(output.getAll()).contains("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: daily_sales_broken_store.csv");
        try (var tree = Files.walk(p.processed())) {
            assertThat(tree.filter(Files::isRegularFile).map(f -> f.getFileName().toString()).toList())
                    .contains("daily_sales_financial.enc").doesNotContain("daily_sales_financial.csv", "daily_sales_usa.csv");
        }
        generator.run(); // La marca evita regenerar los datos después de reiniciar.
        service.processPending();
        assertThat(list(p.bucket())).hasSize(4);
        assertThat(list(p.input())).isEmpty();
    }

    @Test void realSpringSchedulerRunsWithoutManualCall() throws Exception {
        try (var context = new SpringApplicationBuilder(AadApplication.class)
                .properties("aadtex.base-dir=" + temp, "spring.main.banner-mode=off").run()) {
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(list(p.bucket())).hasSize(4);
                assertThat(list(p.input())).isEmpty();
            });
        }
    }

    @Test void encryptionIsCompatibleAndDoesNotWritePlaintext() throws Exception {
        new MultiLanguageDataGenerator(demo).run();
        byte[] plain = new CryptoService(files).decrypt(p.input().resolve("daily_sales_financial.enc"));
        assertThat(new String(plain, StandardCharsets.UTF_8)).contains("95000.00,FINANCIAL_REPORT");
        assertThat(sales.summarize(new ByteArrayInputStream(plain)).totalSales()).isEqualByComparingTo("95000.00");
        assertThat(list(p.input())).noneMatch(f -> f.toString().endsWith("financial.csv"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"amount", "date", "utf8", "header", "empty", "missing-signature", "truncated-jpeg", "bad-enc", "bad-name"})
    void invalidInputsAreIsolatedAndDoNotPublish(String kind) throws Exception {
        String name = kind.equals("bad-name") ? "unexpected.csv" : "daily_sales_test." + (kind.equals("bad-enc") ? "enc" : "csv");
        byte[] data = ("StoreID,StoreName,SalesAmount,Category,TransactionDate\nID,España,12.50,MEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8);
        if (kind.equals("amount")) data = new String(data, StandardCharsets.UTF_8).replace("12.50", "NaN").getBytes(StandardCharsets.UTF_8);
        if (kind.equals("date")) data = new String(data, StandardCharsets.UTF_8).replace("2026-09-13", "2026-99-99").getBytes(StandardCharsets.UTF_8);
        if (kind.equals("header")) data = "Invalid,CSV\n".getBytes(StandardCharsets.UTF_8);
        if (kind.equals("utf8")) data[data.length - 2] = (byte) 0xff;
        if (kind.equals("empty")) data = new byte[0];
        if (kind.equals("bad-enc")) {
            Files.writeString(temp.resolve("keys/AadTexSecret.key"), CryptoService.DEMO_KEY);
            data = new byte[]{1, 2, 3};
        }
        Files.write(p.input().resolve(name), data);
        if (!kind.equals("missing-signature") && !kind.equals("bad-name")) {
            try (var in = new ClassPathResource("demo/input/signature_spain.jpg").getInputStream()) {
                Files.copy(in, p.input().resolve("signature_test.jpg"));
            }
            if (kind.equals("truncated-jpeg")) Files.write(p.input().resolve("signature_test.jpg"), new byte[]{(byte) 255, (byte) 216, (byte) 255});
        }
        service.processPending();
        assertThat(list(p.bucket())).isEmpty();
        assertThat(list(p.signatures())).isEmpty();
        assertThat(list(p.error())).anyMatch(f -> f.getFileName().toString().equals(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"traversal", "missing", "duplicate", "junk", "signature-first-invalid"})
    void unsafeOrBrokenZipRollsBack(String kind) throws Exception {
        Path source = p.input().resolve("batch_bad.zip");
        if (kind.equals("junk")) Files.writeString(source, "Not a zip");
        else try (var out = new ZipOutputStream(Files.newOutputStream(source))) {
            if (kind.equals("signature-first-invalid")) {
                out.putNextEntry(new ZipEntry("signature_test.jpg"));
                try (var in = new ClassPathResource("demo/input/signature_spain.jpg").getInputStream()) { in.transferTo(out); }
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(kind.equals("traversal") ? "../escaped.csv" : "daily_sales_test.csv"));
            out.write((kind.equals("signature-first-invalid") ? "invalid" : "StoreID,StoreName,SalesAmount,Category,TransactionDate\nID,España,2.00,MEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            if (kind.equals("duplicate")) {
                out.putNextEntry(new ZipEntry("daily_sales_second.csv")); out.write(1); out.closeEntry();
            }
        }
        service.processPending();
        assertThat(list(p.bucket())).isEmpty();
        assertThat(list(p.signatures())).isEmpty();
        assertThat(p.error().resolve("batch_bad.zip")).exists();
        assertThat(temp.resolve("escaped.csv")).doesNotExist();
    }

    @Test void quotedCsvAndMonetaryPrecision() throws Exception {
        String text = "StoreID,StoreName,SalesAmount,Category,TransactionDate\nID,\"Tienda, Ñ y \"\"上海\"\"\",0.10,MEN,2026-09-13\nID,Test,0.20,KIDS,2026-09-13\n";
        var summary = sales.summarize(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertThat(summary.totalSales()).isEqualByComparingTo(new BigDecimal("0.30"));
        assertThat(summary.storeNames()).contains("Tienda, Ñ y \"上海\"");
    }

    @Test void truncatedZipCentralDirectoryIsRejected() throws Exception {
        byte[] zip;
        try (var in = new ClassPathResource("demo/input/batch_usa.zip").getInputStream()) { zip = in.readAllBytes(); }
        Files.write(p.input().resolve("batch_truncated.zip"), Arrays.copyOf(zip, zip.length - 22));
        service.processPending();
        assertThat(p.error().resolve("batch_truncated.zip")).exists();
        assertThat(list(p.bucket())).isEmpty();
    }

    @Test void damagedZipCrcIsRejectedAndSignatureRemoved() throws Exception {
        byte[] jpeg;
        try (var in = new ClassPathResource("demo/input/signature_spain.jpg").getInputStream()) { jpeg = in.readAllBytes(); }
        byte[] zipBytes;
        try (var buffer = new ByteArrayOutputStream(); var out = new ZipOutputStream(buffer)) {
            var entry = new ZipEntry("signature_test.jpg");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(jpeg.length);
            var crc = new CRC32(); crc.update(jpeg); entry.setCrc(crc.getValue());
            out.putNextEntry(entry); out.write(jpeg); out.closeEntry();
            out.putNextEntry(new ZipEntry("daily_sales_test.csv"));
            out.write("StoreID,StoreName,SalesAmount,Category,TransactionDate\nID,Test,1.00,MEN,2026-09-13\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry(); out.finish();
            zipBytes = buffer.toByteArray();
        }
        int nameLength = (zipBytes[26] & 255) | ((zipBytes[27] & 255) << 8);
        int extraLength = (zipBytes[28] & 255) | ((zipBytes[29] & 255) << 8);
        zipBytes[30 + nameLength + extraLength + jpeg.length / 2] ^= 1;
        Files.write(p.input().resolve("batch_crc.zip"), zipBytes);
        service.processPending();
        assertThat(p.error().resolve("batch_crc.zip")).exists();
        assertThat(list(p.signatures())).isEmpty();
        assertThat(list(p.bucket())).isEmpty();
    }

    @Test void binaryCopyIsBounded() throws Exception {
        byte[] oversized = new byte[(int) LimitedInputStream.MAX_BYTES + 1];
        oversized[0] = (byte) 255; oversized[1] = (byte) 216; oversized[2] = (byte) 255;
        assertThatThrownBy(() -> files.copySignature(new ByteArrayInputStream(oversized), "signature_large.jpg", "test"))
                .isInstanceOf(IOException.class).hasMessageContaining("16 MiB");
        assertThat(list(p.signatures())).isEmpty();
    }

    @Test void signatureAloneUsesBinaryCopy() throws Exception {
        try (var in = new ClassPathResource("demo/input/signature_spain.jpg").getInputStream()) { Files.copy(in, p.input().resolve("signature_extra.jpg")); }
        service.processPending();
        assertThat(list(p.signatures())).hasSize(1);
        assertThat(list(p.bucket())).isEmpty();
    }

    @Test void invalidBatchDoesNotBlockNextValidBatch() throws Exception {
        new MultiLanguageDataGenerator(demo).run();
        Files.writeString(p.input().resolve("aaa.csv"), "wrong name");
        service.processPending();
        assertThat(list(p.bucket())).hasSize(4);
        assertThat(p.error().resolve("aaa.csv")).exists();
    }

    @Test void demoCanBeDisabled() throws Exception {
        p.setDemoEnabled(false);
        new MultiLanguageDataGenerator(demo).run();
        assertThat(list(p.input())).isEmpty();
    }

    @Test void errorNameCollisionDoesNotErasePreviousFile() throws Exception {
        Files.writeString(p.error().resolve("daily_sales_test.csv"), "previous evidence");
        Files.createFile(p.input().resolve("daily_sales_test.csv"));
        service.processPending();
        assertThat(list(p.error())).hasSize(2);
        assertThat(Files.readString(p.error().resolve("daily_sales_test.csv"))).isEqualTo("previous evidence");
    }

    private List<Path> list(Path dir) throws IOException {
        try (var files = Files.list(dir)) { return files.toList(); }
    }
    private void assertTotal(String json, String amount) {
        var match = java.util.regex.Pattern.compile("\"totalSales\"\\s*:\\s*([0-9.]+)").matcher(json);
        assertThat(match.find()).isTrue();
        assertThat(new BigDecimal(match.group(1))).isEqualByComparingTo(amount);
    }
}
