package com.aadtex.ingestion;

import com.aadtex.ingestion.config.IngestionProperties;
import com.aadtex.ingestion.io.LimitedInputStream;
import com.aadtex.ingestion.repository.*;
import com.aadtex.ingestion.service.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class RepositoryTest {
    @TempDir(factory = IngestionTest.WorkspaceTempFactory.class) Path temp;
    IngestionProperties properties;
    StoreFileRepository files;

    @BeforeEach void setUp() throws IOException {
        properties = new IngestionProperties();
        properties.setBaseDir(temp);
        files = new StoreFileRepository(properties);
        files.initialize();
    }

    @Test void lockIsExclusiveAndReleasedWhenClosed() throws Exception {
        var other = new StoreFileRepository(properties);
        try (var lock = files.tryAcquireLock()) {
            assertThat(lock).isNotNull();
            assertThat(other.tryAcquireLock()).isNull();
        }
        try (var lock = other.tryAcquireLock()) { assertThat(lock).isNotNull(); }
    }

    @Test void bucketFailureRestoresOriginalsBeforeQuarantine() throws Exception {
        var demo = new DemoDataService(properties, files, new DemoDataRepository(properties));
        demo.generate();
        var unavailable = new CloudBucketRepository(properties) {
            @Override public Path saveSummary(String id, String json) throws IOException {
                throw new IOException("Bucket no disponible");
            }
        };
        var ingestion = new StoreIngestionService(files, new SalesService(new CsvSalesRepository()),
                new CryptoService(files), new CloudBucketService(unavailable));
        ingestion.processPending();
        try (var bucket = Files.list(properties.bucket()); var signatures = Files.list(properties.signatures());
             var errors = Files.list(properties.error()); var input = Files.list(properties.input())) {
            assertThat(bucket.toList()).isEmpty();
            assertThat(signatures.toList()).isEmpty();
            assertThat(input.toList()).isEmpty();
            assertThat(errors.toList()).hasSize(8);
        }
        try (var processed = Files.walk(properties.processed())) {
            assertThat(processed.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test void encryptedInputCannotExceedMemoryLimit() throws Exception {
        Path encrypted = files.inputFile("daily_sales_large.enc");
        Files.write(encrypted, new byte[(int) LimitedInputStream.MAX_BYTES + 1]);
        assertThatThrownBy(() -> files.readEncrypted(encrypted)).isInstanceOf(IOException.class)
                .hasMessageContaining("Límite de lectura");
    }

    @Test void existingSignatureIsPreservedIfDestinationAlreadyExists() throws Exception {
        Path original = properties.signatures().resolve("signature_keep_fixed.jpg");
        Files.writeString(original, "existing evidence");
        assertThatThrownBy(() -> files.copySignature(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}),
                "signature_keep.jpg", "fixed")).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(original)).isEqualTo("existing evidence");
    }
}
