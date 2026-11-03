package com.aadtex.ingestion;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDataGenerator.class);
    private final IngestionProperties properties;
    public MultiLanguageDataGenerator(IngestionProperties properties) { this.properties = properties; }

    @Override public void run(String... args) throws Exception {
        FileOperations.initialize(properties);
        Path marker = properties.getBaseDir().resolve(".demo-generated");
        if (!properties.isDemoEnabled() || Files.exists(marker)) return;
        Path key = properties.getBaseDir().resolve("keys/AadTexSecret.key");
        if (!Files.exists(key)) Files.writeString(key, CryptoService.DEMO_KEY, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        for (String name : new String[]{"daily_sales_spain.csv", "daily_sales_china.csv", "daily_sales_financial.enc",
                "signature_spain.jpg", "signature_china.jpg", "signature_financial.jpg", "batch_usa.zip", "daily_sales_broken_store.csv"}) {
            Path target = properties.input().resolve(name);
            if (!Files.exists(target)) {
                try (var in = new ClassPathResource("demo/input/" + name).getInputStream()) { Files.copy(in, target); }
            }
        }
        Files.writeString(marker, "Datos de ejemplo generados una sola vez.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        log.info("Datos multilenguaje preparados en {}", properties.input());
    }
}
