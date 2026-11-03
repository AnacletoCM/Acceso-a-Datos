package com.aadtex.ingestion;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aadtex")
public class IngestionProperties {
    private Path baseDir = Path.of(System.getProperty("os.name").startsWith("Windows")
            ? "C:/workspace/store_data" : "/workspace/store_data");
    private boolean demoEnabled = true;
    private boolean explicitBaseDir;
    private boolean localFallback;

    public Path getBaseDir() { return baseDir.toAbsolutePath().normalize(); }
    public void setBaseDir(Path baseDir) { this.baseDir = baseDir; this.explicitBaseDir = true; }
    boolean useLocalFallback() {
        if (explicitBaseDir || localFallback) return false;
        baseDir = Path.of("store_data");
        localFallback = true;
        return true;
    }
    public boolean isDemoEnabled() { return demoEnabled; }
    public void setDemoEnabled(boolean demoEnabled) { this.demoEnabled = demoEnabled; }
    public Path input() { return getBaseDir().resolve("input"); }
    public Path processed() { return getBaseDir().resolve("output/processed"); }
    public Path signatures() { return processed().resolve("signatures"); }
    public Path error() { return getBaseDir().resolve("output/error"); }
    public Path bucket() { return getBaseDir().resolve("output/bucket/aadtex-daily-reports/2026/09"); }
}
