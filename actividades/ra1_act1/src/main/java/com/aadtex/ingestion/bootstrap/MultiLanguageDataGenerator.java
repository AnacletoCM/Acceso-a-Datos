package com.aadtex.ingestion.bootstrap;

import com.aadtex.ingestion.service.DemoDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private final DemoDataService demoData;
    public MultiLanguageDataGenerator(DemoDataService demoData) { this.demoData = demoData; }

    @Override public void run(String... args) throws Exception {
        demoData.generate();
    }
}
