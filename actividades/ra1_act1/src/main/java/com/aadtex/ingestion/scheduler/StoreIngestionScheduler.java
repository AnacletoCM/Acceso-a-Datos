package com.aadtex.ingestion.scheduler;

import com.aadtex.ingestion.service.StoreIngestionService;

import java.io.IOException;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StoreIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);
    private final StoreIngestionService ingestion;
    private volatile boolean ready;
    public StoreIngestionScheduler(StoreIngestionService ingestion) { this.ingestion = ingestion; }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { ready = true; }

    @Scheduled(fixedRate = 5000)
    public void processStoreBatches() {
        if (!ready) return; // El generador CommandLineRunner debe terminar primero.
        try { ingestion.processPending(); }
        catch (IOException e) { log.error("No se ha podido escanear input: {}", e.getMessage()); }
    }
}
