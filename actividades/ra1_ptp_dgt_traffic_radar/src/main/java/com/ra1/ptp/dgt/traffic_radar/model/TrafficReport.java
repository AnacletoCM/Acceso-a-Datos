package com.ra1.ptp.dgt.traffic_radar.model;

import java.math.BigDecimal;
import java.util.List;

public record TrafficReport(String radarId, String status, int totalVehicles,
        int totalInfractions, double maxSpeed, BigDecimal totalFines, List<Vehicle> infractions) {

    public TrafficReport {
        infractions = List.copyOf(infractions);
    }
}
