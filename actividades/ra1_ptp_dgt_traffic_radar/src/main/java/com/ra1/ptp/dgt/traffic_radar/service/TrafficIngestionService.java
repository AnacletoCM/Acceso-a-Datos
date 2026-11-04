package com.ra1.ptp.dgt.traffic_radar.service;

import com.ra1.ptp.dgt.traffic_radar.model.TrafficReport;
import com.ra1.ptp.dgt.traffic_radar.model.Vehicle;
import com.ra1.ptp.dgt.traffic_radar.repository.TrafficRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TrafficIngestionService {

    private final TrafficRepository trafficRepository;

    public TrafficIngestionService(TrafficRepository trafficRepository) {
        this.trafficRepository = trafficRepository;
    }

    public void processTrafficData() throws IOException {
        List<Vehicle> vehicles = trafficRepository.findAll();
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

        TrafficReport report = new TrafficReport("RADAR-M30-KM12", "READY_FOR_DISPATCH",
                vehicles.size(), infractions.size(), maxSpeed, totalFines, infractions);
        trafficRepository.saveReport(report);
    }
}
