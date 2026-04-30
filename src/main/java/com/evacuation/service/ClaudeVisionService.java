package com.evacuation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Service
public class ClaudeVisionService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> detectZones(String imagePath) {
        try {
            String python = System.getProperty("os.name").toLowerCase().contains("win")
                ? "python" : "python3";
            String script = System.getProperty("user.dir") + "/python-model/detect_zones.py";

            ProcessBuilder pb = new ProcessBuilder(python, script, imagePath);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            BufferedReader stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stderr  = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder out = new StringBuilder();
            String line;
            while ((line = stdout.readLine()) != null) out.append(line);

            StringBuilder err = new StringBuilder();
            while ((line = stderr.readLine()) != null) err.append(line);

            process.waitFor();

            if (err.length() > 0)
                System.err.println("Zone detection warning: " + err);

            String json = out.toString().trim();
            if (json.isEmpty() || json.equals("[]"))
                return Collections.emptyList();

            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            System.err.println("Zone detection error: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
