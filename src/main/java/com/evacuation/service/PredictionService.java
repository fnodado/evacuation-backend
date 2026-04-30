package com.evacuation.service;

import com.evacuation.model.PredictionResult;
import com.evacuation.model.Zone;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class PredictionService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String findPythonCommand() {
        String[] candidates = {"python3.11", "python3", "python"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor();
                System.out.println("Found Python command: " + cmd);
                return cmd;
            } catch (Exception e) {
                System.out.println("Python command not found: " + cmd);
            }
        }
        return "python3";
    }

    private String findPythonDir() {
        // Check current directory first (Railway deployment)
        File currentDir = new File(".");
        if (new File(currentDir, "predict.py").exists()) {
            System.out.println("Found predict.py in current dir: " + currentDir.getAbsolutePath());
            return ".";
        }
        // Check /app directory (Railway container)
        File appDir = new File("/app");
        if (new File(appDir, "predict.py").exists()) {
            System.out.println("Found predict.py in /app");
            return "/app";
        }
        // Check python-model subdirectory (local dev)
        String localModel = System.getProperty("user.dir") + "/python-model";
        if (new File(localModel, "predict.py").exists()) {
            System.out.println("Found predict.py in python-model/");
            return localModel;
        }
        System.out.println("predict.py not found, using current dir");
        return ".";
    }

    public String predictCongestion(int peopleCount, int maxCapacity, String zoneType,
                                     String movementSpeed, String timeOfDay, int emergencyFlag) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("people_count", peopleCount);
            input.put("max_capacity", maxCapacity);
            input.put("zone_type", zoneType);
            input.put("movement_speed", movementSpeed);
            input.put("time_of_day", timeOfDay);
            input.put("emergency_flag", emergencyFlag);

            String inputJson = objectMapper.writeValueAsString(input);

            String pythonCmd = findPythonCommand();
            String pythonDir = findPythonDir();

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, "predict.py", inputJson);
            pb.directory(new File(pythonDir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line);
            process.waitFor();

            if (output.length() > 0) {
                String raw = output.toString();
                int start = raw.lastIndexOf("{");
                int end = raw.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> result = objectMapper.readValue(raw.substring(start, end + 1), Map.class);
                    String level = result.get("congestion_level");
                    if (level != null && !level.isBlank()) return level;
                }
            }
        } catch (Exception e) {
            System.err.println("Python prediction error: " + e.getMessage());
        }
        return fallbackPrediction(peopleCount, maxCapacity, zoneType, emergencyFlag);
    }

    public String predictCongestion(Zone zone) {
        return predictCongestion(
            zone.getPeopleCount() != null ? zone.getPeopleCount() : 0,
            zone.getMaxCapacity() != null ? zone.getMaxCapacity() : 100,
            zone.getZoneType() != null ? zone.getZoneType() : "hallway",
            zone.getMovementSpeed() != null ? zone.getMovementSpeed() : "normal",
            zone.getTimeOfDay() != null ? zone.getTimeOfDay() : "12:00",
            zone.getEmergencyFlag() != null && zone.getEmergencyFlag() ? 1 : 0
        );
    }

    private String fallbackPrediction(int people, int capacity, String zoneType, int emergencyFlag) {
        double ratio = capacity > 0 ? (double) people / capacity : 0;
        if (emergencyFlag == 1) {
            if ("hallway".equals(zoneType) || "lobby".equals(zoneType))
                return ratio >= 0.7 ? "Critical" : "High";
            if ("stairwell".equals(zoneType) || "exit".equals(zoneType)) return "High";
            return ratio >= 0.64 ? "High" : "Moderate";
        }
        if (ratio >= 0.85) return "Critical";
        if (ratio >= 0.65) return "High";
        if (ratio >= 0.40) return "Moderate";
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
            if (zone.getPeopleCount() == null) zone.setPeopleCount(simulatePeopleCount(zone.getZoneType()));
            if (zone.getMovementSpeed() == null) zone.setMovementSpeed("fast");
            zone.setEmergencyFlag(true);

            String level = predictCongestion(zone);
            String recommendedExit = (level.equals("Critical") && exits.size() > 1)
                ? exits.get(1) : exits.get(0);

            results.add(new PredictionResult(
                zone.getId(), zone.getName(), level, recommendedExit,
                zone.getPeopleCount(), zone.getMovementSpeed(), true
            ));
        }
        return results;
    }

    private int simulatePeopleCount(String zoneType) {
        Random rand = new Random();
        if (zoneType == null) return rand.nextInt(20) + 10;
        return switch (zoneType) {
            case "hallway" -> rand.nextInt(60) + 40;
            case "stairwell" -> rand.nextInt(40) + 30;
            case "classroom" -> rand.nextInt(30) + 20;
            case "lobby" -> rand.nextInt(80) + 50;
            case "exit" -> rand.nextInt(50) + 30;
            default -> rand.nextInt(20) + 10;
        };
    }
}
