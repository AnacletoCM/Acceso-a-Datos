package com.ra1.ptp.dgt.traffic_radar;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrafficRadarApplicationTests {

    @TempDir
    Path directory;

    @Test
    void missingDirectoryStructureIsCreatedOnStartup() throws Exception {
        Path base = directory.resolve("workspace/traffic_data");
        assertFalse(Files.exists(base));

        new TrafficIngestionService(base.toString()).processTrafficData();

        for (String folder : new String[] {
                "keys", "input", "output/processed", "output/error", "output/payload"}) {
            assertTrue(Files.isDirectory(base.resolve(folder)), folder);
        }
        assertTrue(Files.readString(base.resolve("output/payload/dgt_dispatch_payload.json"))
                .contains("\"totalVehicles\": 0"));
        assertFalse(Files.exists(base.resolve("keys/dgt_secret.key")));
    }

    @Test
    void processTrafficData() throws Exception {
        Files.createDirectories(directory.resolve("keys"));
        Files.createDirectories(directory.resolve("input"));
        byte[] key = "DgtSecretKey2026".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("keys/dgt_secret.key"), key);
        String header = "Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros\n";
        Files.writeString(directory.resolve("input/radar_m30.csv"), header + """
                1234-KBB,Peugeot 308 (España),115.5,90,100.00
                5678-CJK,BYD 唐 (China Edition),135.0,90,300.00
                9012-LMN,Seat León Cupra,85.0,90,0.00
                """, StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] encrypted = cipher.doFinal((header
                + "0001-DIR,Audi A8 Blindado Oficial,180.0,90,600.00\n")
                .getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve("input/radar_movil_guardia_civil.enc"), encrypted);
        Files.createFile(directory.resolve("input/radar_corrupto.csv"));

        new TrafficIngestionService(directory.toString()).processTrafficData();

        assertEquals("""
                {
                  "radarId": "RADAR-M30-KM12",
                  "status": "READY_FOR_DISPATCH",
                  "totalVehicles": 4,
                  "totalInfractions": 3,
                  "maxSpeed": 180.0,
                  "totalFines": 1000.00,
                  "infractions": [
                    {
                      "plate": "1234-KBB",
                      "model": "Peugeot 308 (España)",
                      "speed": 115.5,
                      "fine": 100.00
                    },
                    {
                      "plate": "5678-CJK",
                      "model": "BYD 唐 (China Edition)",
                      "speed": 135.0,
                      "fine": 300.00
                    },
                    {
                      "plate": "0001-DIR",
                      "model": "Audi A8 Blindado Oficial",
                      "speed": 180.0,
                      "fine": 600.00
                    }
                  ]
                }
                """, Files.readString(directory.resolve("output/payload/dgt_dispatch_payload.json"),
                        StandardCharsets.UTF_8));
        assertTrue(Files.exists(directory.resolve("output/processed/radar_m30.csv")));
        assertArrayEquals(encrypted,
                Files.readAllBytes(directory.resolve("output/processed/radar_movil_guardia_civil.enc")));
        assertEquals(0, Files.size(directory.resolve("output/error/radar_corrupto.csv")));
        try (var files = Files.list(directory.resolve("input"))) {
            assertEquals(0, files.count());
        }
        try (var files = Files.walk(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .equals("radar_movil_guardia_civil.csv")));
        }
    }

    @Test
    void malformedFileDoesNotContributePartialRecords() throws Exception {
        Files.createDirectories(directory.resolve("input"));
        Files.writeString(directory.resolve("input/radar_m30.csv"), """
                Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros
                1234-KBB,Peugeot 308 (España),115.5,90,100.00
                registro incorrecto
                """, StandardCharsets.UTF_8);

        new TrafficIngestionService(directory.toString()).processTrafficData();

        assertTrue(Files.exists(directory.resolve("output/error/radar_m30.csv")));
        String json = Files.readString(directory.resolve("output/payload/dgt_dispatch_payload.json"));
        assertTrue(json.contains("\"totalVehicles\": 0"));
        assertTrue(json.contains("\"totalFines\": 0.00"));
        assertFalse(json.contains("1234-KBB"));
    }

    @Test
    void invalidUtf8AndEncryptedFilesAreIsolated() throws Exception {
        Files.createDirectories(directory.resolve("input"));
        Files.createDirectories(directory.resolve("keys"));
        Files.writeString(directory.resolve("keys/dgt_secret.key"), "DgtSecretKey2026",
                StandardCharsets.UTF_8);
        byte[] header = "Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros\n"
                .getBytes(StandardCharsets.UTF_8);
        byte[] invalid = java.util.Arrays.copyOf(header, header.length + 2);
        invalid[header.length] = (byte) 0xc3;
        invalid[header.length + 1] = (byte) 0x28;
        Files.write(directory.resolve("input/radar_m30.csv"), invalid);
        Files.write(directory.resolve("input/radar_movil_guardia_civil.enc"), new byte[] {1, 2, 3});

        new TrafficIngestionService(directory.toString()).processTrafficData();

        assertArrayEquals(invalid, Files.readAllBytes(directory.resolve("output/error/radar_m30.csv")));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(
                directory.resolve("output/error/radar_movil_guardia_civil.enc")));
        assertTrue(Files.readString(directory.resolve("output/payload/dgt_dispatch_payload.json"))
                .contains("\"totalVehicles\": 0"));
    }

    @Test
    void totalsAreCalculatedFromRecordsAndLimitEqualityIsNotAnInfraction() throws Exception {
        Files.createDirectories(directory.resolve("input"));
        Files.writeString(directory.resolve("input/radar_m30.csv"), """
                Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros
                1111-ABC,Seat León,100.0,90,10.01
                2222-DEF,BYD 唐,90.0,90,0.00
                """, StandardCharsets.UTF_8);

        new TrafficIngestionService(directory.toString()).processTrafficData();

        String json = Files.readString(directory.resolve("output/payload/dgt_dispatch_payload.json"));
        assertTrue(json.contains("\"totalVehicles\": 2"));
        assertTrue(json.contains("\"totalInfractions\": 1"));
        assertTrue(json.contains("\"maxSpeed\": 100.0"));
        assertTrue(json.contains("\"totalFines\": 10.01"));
        assertTrue(json.contains("1111-ABC"));
        assertFalse(json.contains("2222-DEF"));
    }
}
