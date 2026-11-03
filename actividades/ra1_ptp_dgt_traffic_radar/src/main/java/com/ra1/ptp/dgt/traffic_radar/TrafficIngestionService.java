package com.ra1.ptp.dgt.traffic_radar;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TrafficIngestionService {

    private final Path basePath;

    public TrafficIngestionService(
            @Value("${traffic.data.path:c:/workspace/traffic_data/}") String basePath) {
        this.basePath = Path.of(basePath);
    }

    public void processTrafficData() throws IOException {
        Files.createDirectories(basePath.resolve("keys"));
        Files.createDirectories(basePath.resolve("input"));
        Path processed = basePath.resolve("output/processed");
        Path error = basePath.resolve("output/error");
        Path payload = basePath.resolve("output/payload");
        Files.createDirectories(processed);
        Files.createDirectories(error);
        Files.createDirectories(payload);

        List<Path> files;
        try (var paths = Files.list(basePath.resolve("input"))) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        List<Vehicle> vehicles = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (Files.size(file) == 0) {
                moveFile(file, error.resolve(name));
                System.out.println("[ALERTA DGT] Fichero corrupto detectado y aislado: " + name);
                continue;
            }
            if (!name.endsWith(".csv") && !name.endsWith(".enc")) {
                continue;
            }

            List<Vehicle> batch;
            try {
                if (name.endsWith(".enc")) {
                    byte[] key = Files.readAllBytes(basePath.resolve("keys/dgt_secret.key"));
                    Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
                    byte[] plaintext = cipher.doFinal(Files.readAllBytes(file));
                    try (InputStream input = new ByteArrayInputStream(plaintext)) {
                        batch = readVehicles(input);
                    }
                } else {
                    try (InputStream input = Files.newInputStream(file)) {
                        batch = readVehicles(input);
                    }
                }
            } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
                moveFile(file, error.resolve(name));
                System.out.println("[ALERTA DGT] Fichero corrupto detectado y aislado: " + name);
                System.err.println(e.getMessage());
                continue;
            }

            moveFile(file, processed.resolve(name));
            vehicles.addAll(batch);
            System.out.println("[DGT] Fichero procesado: " + name);
        }

        Path destination = payload.resolve("dgt_dispatch_payload.json");
        Files.writeString(destination, createPayload(vehicles), StandardCharsets.UTF_8);
        System.out.println("[DGT] Payload generado: " + destination);
    }

    private List<Vehicle> readVehicles(InputStream input) throws IOException {
        List<Vehicle> vehicles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()))) {
            String header = reader.readLine();
            if (!"Matricula,MarcaModelo,VelocidadMedida,LimiteVia,SancionEuros".equals(header)) {
                throw new IOException("Cabecera CSV incorrecta");
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length != 5) {
                    throw new IOException("Registro CSV incorrecto");
                }
                double speed = Double.parseDouble(fields[2]);
                double limit = Double.parseDouble(fields[3]);
                if (!Double.isFinite(speed) || !Double.isFinite(limit)) {
                    throw new IOException("Velocidad o límite incorrectos");
                }
                vehicles.add(new Vehicle(fields[0], fields[1], speed, limit,
                        new BigDecimal(fields[4])));
            }
        }
        return vehicles;
    }

    private void moveFile(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String createPayload(List<Vehicle> vehicles) {
        List<Vehicle> infractions = new ArrayList<>();
        double maxSpeed = 0.0;
        BigDecimal totalFines = BigDecimal.ZERO;
        for (Vehicle vehicle : vehicles) {
            maxSpeed = Math.max(maxSpeed, vehicle.speed());
            totalFines = totalFines.add(vehicle.fine());
            if (vehicle.speed() > vehicle.limit()) {
                infractions.add(vehicle);
            }
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"radarId\": \"RADAR-M30-KM12\",\n");
        json.append("  \"status\": \"READY_FOR_DISPATCH\",\n");
        json.append("  \"totalVehicles\": ").append(vehicles.size()).append(",\n");
        json.append("  \"totalInfractions\": ").append(infractions.size()).append(",\n");
        json.append("  \"maxSpeed\": ").append(maxSpeed).append(",\n");
        json.append("  \"totalFines\": ").append(money(totalFines)).append(",\n");
        json.append("  \"infractions\": [");
        for (int i = 0; i < infractions.size(); i++) {
            Vehicle vehicle = infractions.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("\n    {\n");
            json.append("      \"plate\": \"").append(escape(vehicle.plate())).append("\",\n");
            json.append("      \"model\": \"").append(escape(vehicle.model())).append("\",\n");
            json.append("      \"speed\": ").append(vehicle.speed()).append(",\n");
            json.append("      \"fine\": ").append(money(vehicle.fine())).append("\n");
            json.append("    }");
        }
        if (!infractions.isEmpty()) {
            json.append("\n  ");
        }
        json.append("]\n}\n");
        return json.toString();
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private record Vehicle(String plate, String model, double speed, double limit, BigDecimal fine) {
    }
}
