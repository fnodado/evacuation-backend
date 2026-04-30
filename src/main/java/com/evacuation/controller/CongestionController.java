package com.evacuation.controller;

import com.evacuation.service.PredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/congestion")
@CrossOrigin(origins = "*")
public class CongestionController {

    @Autowired
    private PredictionService predictionService;

    @PostMapping("/predict")
    public ResponseEntity<?> predict(@RequestBody Map<String, Object> body) {
        try {
            int peopleCount   = ((Number) body.get("peopleCount")).intValue();
            int maxCapacity   = ((Number) body.get("maxCapacity")).intValue();
            String zoneType      = (String) body.get("zoneType");
            String movementSpeed = (String) body.get("movementSpeed");
            String timeOfDay     = (String) body.get("timeOfDay");
            int emergencyFlag = ((Number) body.getOrDefault("emergencyFlag", 0)).intValue();

            String level = predictionService.predictCongestion(
                peopleCount, maxCapacity, zoneType, movementSpeed, timeOfDay, emergencyFlag);

            String recommendation = switch (level) {
                case "Low"      -> "Zone is clear. Normal movement.";
                case "Moderate" -> "Monitor zone. Consider redistributing people.";
                case "High"     -> "Redirect people to alternate zones immediately.";
                case "Critical" -> "Immediate action required. Clear the zone now.";
                default         -> "Monitor zone.";
            };

            double densityRatio = maxCapacity > 0 ? (double) peopleCount / maxCapacity : 0;

            return ResponseEntity.ok(Map.of(
                "congestionLevel",  level,
                "recommendation",   recommendation,
                "zoneType",         zoneType,
                "peopleCount",      peopleCount,
                "maxCapacity",      maxCapacity,
                "densityRatio",     Math.round(densityRatio * 100.0) / 100.0
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
