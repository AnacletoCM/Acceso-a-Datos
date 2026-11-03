package com.aadtex.ingestion;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aadtex")
public class IngestionProperties {
    private Path baseDir = Path.of(System.getProperty("os.name").startsWith("Windows")
            ? "C:/workspace/store_data" : "/workspace/store_data");
    private boolean demoEnabled = true;

    public Path getBaseDir() { return baseDir.toAbsolutePath().normalize(); }
    public void setBaseDir(Path baseDir) { this.baseDir = baseDir; }
    public boolean isDemoEnabled() { return demoEnabled; }
    public void setDemoEnabled(boolean demoEnabled) { this.demoEnabled = demoEnabled; }
    public Path input() { return getBaseDir().resolve("input"); }
    public Path processed() { return getBaseDir().resolve("output/processed"); }
    public Path signatures() { return processed().resolve("signatures"); }
    public Path error() { return getBaseDir().resolve("output/error"); }
    public Path bucket() { return getBaseDir().resolve("output/bucket/aadtex-daily-reports/2026/09"); }
}
