package com.aadtex.ingestion.service;

import com.aadtex.ingestion.config.IngestionProperties;
import com.aadtex.ingestion.repository.DemoDataRepository;
import com.aadtex.ingestion.repository.StoreFileRepository;
import java.io.IOException;
import java.util.List;
import org.slf4j.*;
import org.springframework.stereotype.Service;

@Service
public class DemoDataService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private static final List<String> EXAMPLES = List.of("daily_sales_spain.csv", "daily_sales_china.csv",
            "daily_sales_financial.enc", "signature_spain.jpg", "signature_china.jpg", "signature_financial.jpg",
            "batch_usa.zip", "daily_sales_broken_store.csv");
    private final IngestionProperties properties;
    private final StoreFileRepository files;
    private final DemoDataRepository demo;

    public DemoDataService(IngestionProperties properties, StoreFileRepository files, DemoDataRepository demo) {
        this.properties = properties; this.files = files; this.demo = demo;
    }

    public void generate() throws IOException {
        files.initialize();
        if (!properties.isDemoEnabled() || demo.isGenerated()) return;
        demo.createKeyIfMissing(CryptoService.DEMO_KEY);
        for (String name : EXAMPLES) demo.copyInputIfMissing(name);
        demo.markGenerated();
        log.info("Datos multilenguaje preparados en {}", files.inputDirectory());
    }
}
