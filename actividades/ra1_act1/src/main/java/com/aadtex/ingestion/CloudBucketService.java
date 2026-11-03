package com.aadtex.ingestion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CloudBucketService {
    private final IngestionProperties properties;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public CloudBucketService(IngestionProperties properties) { this.properties = properties; }

    public Path publish(String id, String source, CsvSalesReader.Sales sales, String signature) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchId", id);
        summary.put("sourceFile", source);
        summary.put("processedAt", Instant.now().toString());
        summary.put("transactions", sales.transactions());
        summary.put("totalSales", sales.totalSales());
        summary.put("storeNames", sales.storeNames());
        summary.put("signature", "output/processed/signatures/" + signature);
        summary.put("status", "PROCESSED");
        Path target = properties.bucket().resolve("summary_batch_" + id + ".json");
        Path pending = Files.createTempFile(properties.bucket(), ".summary-", ".pending");
        try {
            Files.writeString(pending, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n", StandardCharsets.UTF_8);
            FileOperations.move(pending, target);
            return target;
        } finally { Files.deleteIfExists(pending); }
    }
}
