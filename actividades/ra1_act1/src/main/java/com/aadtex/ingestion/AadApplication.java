package com.aadtex.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IngestionProperties.class)
public class AadApplication {
    public static void main(String[] args) {
        SpringApplication.run(AadApplication.class, args);
    }
}
