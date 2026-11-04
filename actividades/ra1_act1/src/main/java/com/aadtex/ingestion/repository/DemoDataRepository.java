package com.aadtex.ingestion.repository;

import com.aadtex.ingestion.config.IngestionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

@Repository
public class DemoDataRepository {
    private final IngestionProperties properties;
    public DemoDataRepository(IngestionProperties properties) { this.properties = properties; }

    public boolean isGenerated() { return Files.exists(marker()); }

    public void createKeyIfMissing(String key) throws IOException {
        Path target = properties.getBaseDir().resolve("keys/AadTexSecret.key");
        if (!Files.exists(target)) Files.writeString(target, key, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public void copyInputIfMissing(String name) throws IOException {
        Path target = properties.input().resolve(name);
        if (!Files.exists(target)) {
            try (var input = new ClassPathResource("demo/input/" + name).getInputStream()) {
                Files.copy(input, target);
            }
        }
    }

    public void markGenerated() throws IOException {
        Files.writeString(marker(), "Datos de ejemplo generados una sola vez.\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private Path marker() { return properties.getBaseDir().resolve(".demo-generated"); }
}
