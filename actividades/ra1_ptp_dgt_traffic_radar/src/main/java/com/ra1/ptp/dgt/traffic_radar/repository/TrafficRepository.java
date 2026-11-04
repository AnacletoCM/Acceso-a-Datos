package com.ra1.ptp.dgt.traffic_radar.repository;

import com.ra1.ptp.dgt.traffic_radar.model.TrafficReport;
import com.ra1.ptp.dgt.traffic_radar.model.Vehicle;
import java.io.IOException;
import java.util.List;

public interface TrafficRepository {

    List<Vehicle> findAll() throws IOException;

    void saveReport(TrafficReport report) throws IOException;
}
