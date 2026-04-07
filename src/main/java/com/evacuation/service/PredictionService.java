package com.evacuation.service;

import com.evacuation.model.PredictionResult;
import com.evacuation.model.Zone;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;

@Service
public class PredictionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PYTHON_DIR = "C:\\Users\\USER\\Documents\\thesis\\evacuation-python";

    public String predictCongestion(Zone zone) {
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("people_count", zone.getPeopleCount() != null ? zone.getPeopleCount() : 0);
            input.put("max_capacity", zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100);
            input.put("zone_type", zone.getZoneType() != null ? zone.getZoneType() : "hallway");
            input.put("movement_speed", zone.getMovementSpeed() != null ? zone.getMovementSpeed() : "normal");
            input.put("time_of_day", zone.getTimeOfDay() != null ? zone.getTimeOfDay() : "12:00");
            input.put("emergency_flag", zone.getEmergencyFlag() != null && zone.getEmergencyFlag() ? 1 : 0);

            String inputJson = objectMapper.writeValueAsString(input);
            System.out.println("Calling Python with: " + inputJson);

            ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c",
                "py", "-3.11", "predict.py", inputJson
            );
            pb.directory(new File(PYTHON_DIR));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                System.out.println("Python output: " + line);
            }

            int exitCode = process.waitFor();
            System.out.println("Python exit code: " + exitCode);

            if (output.length() == 0) {
                System.err.println("Python returned empty output");
                return fallbackPrediction(zone);
            }

            String outputStr = output.toString();
            int jsonStart = outputStr.lastIndexOf("{");
            int jsonEnd = outputStr.lastIndexOf("}");
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                String jsonPart = outputStr.substring(jsonStart, jsonEnd + 1);
                Map<String, String> result = objectMapper.readValue(jsonPart, Map.class);
                String level = result.getOrDefault("congestion_level", null);
                if (level != null && !level.isEmpty()) {
                    System.out.println("Python predicted: " + level);
                    return level;
                }
            }

            return fallbackPrediction(zone);

        } catch (Exception e) {
            System.err.println("Python error: " + e.getMessage());
            return fallbackPrediction(zone);
        }
    }

    private String fallbackPrediction(Zone zone) {
        if (zone.getEmergencyFlag() != null && zone.getEmergencyFlag()) {
            String type = zone.getZoneType() != null ? zone.getZoneType() : "";
            switch (type) {
                case "hallway": return "Critical";
                case "stairwell": return "High";
                case "exit": return "High";
                case "lobby": return "Critical";
                default: return "Moderate";
            }
        }
        int people = zone.getPeopleCount() != null ? zone.getPeopleCount() : 0;
        int capacity = zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100;
        double ratio = capacity > 0 ? (double) people / capacity : 0;
        if (ratio >= 0.9) return "Critical";
        if (ratio >= 0.7) return "High";
        if (ratio >= 0.4) return "Moderate";
        return "Low";
    }

    public List<PredictionResult> predictEarthquake(List<Zone> zones) {
        List<PredictionResult> results = new ArrayList<>();
        List<String> exits = new ArrayList<>();
        for (Zone z : zones) {
            if (Boolean.TRUE.equals(z.getIsExit())) exits.add(z.getName());
        }
        if (exits.isEmpty()) exits.add("Main Exit");

        for (Zone zone : zones) {
            zone.setEmergencyFlag(true);
            if (zone.getPeopleCount() == null || zone.getPeopleCount() == 0) {
                zone.setPeopleCount(simulatePeopleCount(zone.getZoneType()));
            }
            if (zone.getMovementSpeed() == null) zone.setMovementSpeed("fast");

            String congestionLevel = predictCongestion(zone);
            String recommended = congestionLevel.equals("Critical") && exits.size() > 1
                ? exits.get(1) : exits.get(0);

            results.add(new PredictionResult(
                zone.getId(), zone.getName(), congestionLevel,
                recommended, zone.getPeopleCount(), zone.getMovementSpeed(), true
            ));
        }
        return results;
    }

    private int simulatePeopleCount(String zoneType) {
        Random rand = new Random();
        if (zoneType == null) return rand.nextInt(30) + 10;
        switch (zoneType) {
            case "hallway": return rand.nextInt(60) + 40;
            case "stairwell": return rand.nextInt(40) + 30;
            case "classroom": return rand.nextInt(30) + 20;
            case "lobby": return rand.nextInt(80) + 50;
            case "exit": return rand.nextInt(50) + 30;
            default: return rand.nextInt(20) + 10;
        }
    }
}
