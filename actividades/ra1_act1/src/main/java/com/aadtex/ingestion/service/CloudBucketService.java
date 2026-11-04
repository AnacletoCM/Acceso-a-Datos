package com.aadtex.ingestion.service;

import com.aadtex.ingestion.model.SalesSummary;
import com.aadtex.ingestion.repository.CloudBucketRepository;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CloudBucketService {
    private final CloudBucketRepository bucket;
    private final JsonMapper mapper = JsonMapper.builder().build();
    public CloudBucketService(CloudBucketRepository bucket) { this.bucket = bucket; }

    public Path publish(String id, String source, SalesSummary sales, String signature) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchId", id);
        summary.put("sourceFile", source);
        summary.put("processedAt", Instant.now().toString());
        summary.put("transactions", sales.transactions());
        summary.put("totalSales", sales.totalSales());
        summary.put("storeNames", sales.storeNames());
        summary.put("signature", "output/processed/signatures/" + signature);
        summary.put("status", "PROCESSED");
        return bucket.saveSummary(id, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    }
}
