package com.ra1.ptp.dgt.traffic_radar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TrafficRadarApplication implements CommandLineRunner {

	private final TrafficIngestionService trafficService;

	public TrafficRadarApplication(TrafficIngestionService trafficService) {
		this.trafficService = trafficService;
	}

	public static void main(String[] args) {
		SpringApplication.run(TrafficRadarApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		trafficService.processTrafficData();
	}

}
