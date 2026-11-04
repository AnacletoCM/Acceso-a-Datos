package com.ra1.ptp.dgt.traffic_radar.model;

import java.math.BigDecimal;

public record Vehicle(String plate, String model, double speed, double limit, BigDecimal fine) {
}
