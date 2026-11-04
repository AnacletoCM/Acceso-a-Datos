package com.ra1.ptp.dgt.traffic_radar.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ra1.ptp.dgt.traffic_radar.model.TrafficReport;
import com.ra1.ptp.dgt.traffic_radar.model.Vehicle;
import com.ra1.ptp.dgt.traffic_radar.repository.TrafficRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrafficIngestionServiceTests {

    @Test
    void calculatesReportWithoutFileAccess() throws Exception {
        Vehicle first = new Vehicle("1111-ABC", "Seat León", 100.0, 90.0, new BigDecimal("0.10"));
        Vehicle second = new Vehicle("2222-DEF", "BYD 唐", 150.0, 90.0, new BigDecimal("0.20"));
        Vehicle atLimit = new Vehicle("3333-GHI", "Peugeot 308", 90.0, 90.0, BigDecimal.ZERO);
        StubTrafficRepository repository = new StubTrafficRepository(List.of(first, second, atLimit));

        new TrafficIngestionService(repository).processTrafficData();

        TrafficReport report = repository.savedReport;
        assertNotNull(report);
        assertEquals("RADAR-M30-KM12", report.radarId());
        assertEquals("READY_FOR_DISPATCH", report.status());
        assertEquals(3, report.totalVehicles());
        assertEquals(2, report.totalInfractions());
        assertEquals(150.0, report.maxSpeed());
        assertEquals(new BigDecimal("0.30"), report.totalFines());
        assertEquals(List.of(first, second), report.infractions());
    }

    @Test
    void emptyInputProducesAnEmptyReport() throws Exception {
        StubTrafficRepository repository = new StubTrafficRepository(List.of());

        new TrafficIngestionService(repository).processTrafficData();

        TrafficReport report = repository.savedReport;
        assertEquals(0, report.totalVehicles());
        assertEquals(0, report.totalInfractions());
        assertEquals(0.0, report.maxSpeed());
        assertEquals(BigDecimal.ZERO, report.totalFines());
        assertTrue(report.infractions().isEmpty());
    }

    @Test
    void readingFailureIsPropagatedWithoutSavingAReport() {
        IOException failure = new IOException("No se pueden leer las entradas");
        TrafficRepository repository = new TrafficRepository() {
            @Override
            public List<Vehicle> findAll() throws IOException {
                throw failure;
            }

            @Override
            public void saveReport(TrafficReport report) {
                fail("No debe guardarse un informe si falla la lectura");
            }
        };

        IOException thrown = assertThrows(IOException.class,
                () -> new TrafficIngestionService(repository).processTrafficData());
        assertSame(failure, thrown);
    }

    private static class StubTrafficRepository implements TrafficRepository {

        private final List<Vehicle> vehicles;
        private TrafficReport savedReport;

        private StubTrafficRepository(List<Vehicle> vehicles) {
            this.vehicles = vehicles;
        }

        @Override
        public List<Vehicle> findAll() {
            return vehicles;
        }

        @Override
        public void saveReport(TrafficReport report) {
            savedReport = report;
        }
    }
}
