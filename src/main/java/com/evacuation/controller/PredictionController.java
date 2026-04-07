package com.evacuation.controller;

import com.evacuation.model.PredictionResult;
import com.evacuation.model.Zone;
import com.evacuation.service.PredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PredictionController {

    @Autowired
    private PredictionService predictionService;

    // Health check
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Evacuation Prediction API"
        ));
    }

    // Predict congestion for a single zone
    @PostMapping("/predict")
    public ResponseEntity<Map<String, String>> predict(@RequestBody Zone zone) {
        String level = predictionService.predictCongestion(zone);
        return ResponseEntity.ok(Map.of("congestion_level", level));
    }

    // Trigger earthquake — predict all zones
    @PostMapping("/trigger/earthquake")
    public ResponseEntity<List<PredictionResult>> triggerEarthquake(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zonesData = (List<Map<String, Object>>) body.get("zones");

        List<Zone> zones = zonesData.stream().map(z -> {
            Zone zone = new Zone();
            zone.setId((String) z.get("id"));
            zone.setName((String) z.get("name"));
            zone.setZoneType((String) z.get("zone_type"));
            zone.setIsExit(z.get("is_exit") != null && (Boolean) z.get("is_exit"));
            zone.setMaxCapacity(z.get("max_capacity") != null ?
                ((Number) z.get("max_capacity")).intValue() : 100);
            zone.setPeopleCount(z.get("people_count") != null ?
                ((Number) z.get("people_count")).intValue() : null);
            zone.setMovementSpeed((String) z.getOrDefault("movement_speed", "fast"));
            zone.setEmergencyFlag(true);
            return zone;
        }).toList();

        List<PredictionResult> results = predictionService.predictEarthquake(zones);
        return ResponseEntity.ok(results);
    }

    // Simulate with custom parameters per zone
    @PostMapping("/simulate")
    public ResponseEntity<List<PredictionResult>> simulate(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> zonesData = (List<Map<String, Object>>) body.get("zones");

        List<Zone> zones = zonesData.stream().map(z -> {
            Zone zone = new Zone();
            zone.setId((String) z.get("id"));
            zone.setName((String) z.get("name"));
            zone.setZoneType((String) z.get("zone_type"));
            zone.setIsExit(z.get("is_exit") != null && (Boolean) z.get("is_exit"));
            zone.setMaxCapacity(z.get("max_capacity") != null ?
                ((Number) z.get("max_capacity")).intValue() : 100);
            zone.setPeopleCount(z.get("people_count") != null ?
                ((Number) z.get("people_count")).intValue() : 0);
            zone.setMovementSpeed((String) z.getOrDefault("movement_speed", "normal"));
            zone.setTimeOfDay((String) z.getOrDefault("time_of_day", "12:00"));
            zone.setEmergencyFlag(z.get("emergency_flag") != null &&
                (Boolean) z.get("emergency_flag"));
            return zone;
        }).toList();

        List<PredictionResult> results = predictionService.predictEarthquake(zones);
        return ResponseEntity.ok(results);
    }
}
