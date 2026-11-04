package com.aadtex.ingestion.repository;

import com.aadtex.ingestion.config.IngestionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.stereotype.Repository;

@Repository
public class CloudBucketRepository {
    private final IngestionProperties properties;
    public CloudBucketRepository(IngestionProperties properties) { this.properties = properties; }

    public Path saveSummary(String id, String json) throws IOException {
        Path target = properties.bucket().resolve("summary_batch_" + id + ".json");
        Path pending = Files.createTempFile(properties.bucket(), ".summary-", ".pending");
        try {
            Files.writeString(pending, json + "\n", StandardCharsets.UTF_8);
            FileOperations.move(pending, target);
            return target;
        } finally { Files.deleteIfExists(pending); }
    }
}
