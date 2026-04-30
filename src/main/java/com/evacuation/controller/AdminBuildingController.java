package com.evacuation.controller;

import com.evacuation.model.*;
import com.evacuation.repository.*;
import com.evacuation.service.QrGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminBuildingController {

    @Autowired private BuildingRepository buildingRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private QrGenerationService qrGenerationService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── BUILDINGS ──────────────────────────────────────────────────────────────

    @GetMapping("/buildings")
    public ResponseEntity<?> getBuildings() {
        List<Map<String, Object>> result = buildingRepository.findAll().stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("address", b.getAddress());
            m.put("total_capacity", b.getTotalCapacity());
            m.put("floors", floorRepository.findByBuildingId(b.getId()));
            m.put("zone_count", zoneRepository.findByBuildingId(b.getId()).size());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/buildings")
    public ResponseEntity<?> createBuilding(@RequestBody Map<String, Object> body) {
        Building b = new Building();
        b.setId(UUID.randomUUID().toString());
        b.setName((String) body.get("name"));
        b.setAddress((String) body.get("address"));
        if (body.get("total_capacity") != null)
            b.setTotalCapacity(((Number) body.get("total_capacity")).intValue());
        return ResponseEntity.ok(buildingRepository.save(b));
    }

    @PutMapping("/buildings/{id}")
    public ResponseEntity<?> updateBuilding(@PathVariable String id,
                                             @RequestBody Map<String, Object> body) {
        return buildingRepository.findById(id).map(b -> {
            if (body.containsKey("name")) b.setName((String) body.get("name"));
            if (body.containsKey("address")) b.setAddress((String) body.get("address"));
            if (body.containsKey("total_capacity"))
                b.setTotalCapacity(((Number) body.get("total_capacity")).intValue());
            return ResponseEntity.ok(buildingRepository.save(b));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/buildings/{id}")
    public ResponseEntity<?> deleteBuilding(@PathVariable String id) {
        buildingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Building deleted"));
    }

    // ── FLOORS ─────────────────────────────────────────────────────────────────

    @PostMapping("/buildings/{buildingId}/floors")
    public ResponseEntity<?> addFloor(@PathVariable String buildingId,
                                       @RequestBody Map<String, Object> body) {
        if (!buildingRepository.existsById(buildingId))
            return ResponseEntity.notFound().build();

        Floor floor = new Floor();
        floor.setId(UUID.randomUUID().toString());
        floor.setBuildingId(buildingId);
        floor.setFloorNumber(((Number) body.get("floor_number")).intValue());
        floor.setLabel((String) body.getOrDefault("label",
            "Floor " + body.get("floor_number")));
        return ResponseEntity.ok(floorRepository.save(floor));
    }

    // ── BLUEPRINT + AI ZONE DETECTION ─────────────────────────────────────────

    @PostMapping("/floors/{floorId}/blueprint")
    public ResponseEntity<?> uploadBlueprint(@PathVariable String floorId,
                                              @RequestParam("file") MultipartFile file) {
        try {
            Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new RuntimeException("Floor not found"));

            Path dir = Paths.get(uploadDir, "blueprints").toAbsolutePath();
            Files.createDirectories(dir);
            String fileName = floorId + "-" + file.getOriginalFilename();
            Path filePath = dir.resolve(fileName);
            Files.write(filePath, file.getBytes());

            floor.setBlueprintUrl(uploadDir + "/blueprints/" + fileName);
            floorRepository.save(floor);

            return ResponseEntity.ok(Map.of(
                "blueprint_url", floor.getBlueprintUrl(),
                "zones", zoneRepository.findByFloorId(floorId)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/floors/{floorId}/blueprint")
    public ResponseEntity<?> getBlueprint(@PathVariable String floorId) {
        return floorRepository.findById(floorId).map(f ->
            ResponseEntity.ok(Map.of(
                "floor_id", floorId,
                "blueprint_url", f.getBlueprintUrl() != null ? f.getBlueprintUrl() : "",
                "zones", zoneRepository.findByFloorId(floorId)
            ))
        ).orElse(ResponseEntity.notFound().build());
    }

    // ── ZONES ──────────────────────────────────────────────────────────────────

    @PostMapping("/floors/{floorId}/zones")
    public ResponseEntity<?> createZone(@PathVariable String floorId,
                                         @RequestBody Map<String, Object> body) {
        try {
            Floor floor = floorRepository.findById(floorId)
                .orElseThrow(() -> new RuntimeException("Floor not found"));

            Zone zone = new Zone();
            zone.setId(UUID.randomUUID().toString());
            zone.setFloorId(floorId);
            zone.setBuildingId(floor.getBuildingId());
            zone.setName((String) body.get("name"));
            zone.setZoneType((String) body.get("zone_type"));
            if (body.get("max_capacity") != null)
                zone.setMaxCapacity(((Number) body.get("max_capacity")).intValue());
            if (body.get("is_assembly_point") != null)
                zone.setIsAssemblyPoint((Boolean) body.get("is_assembly_point"));
            if (body.get("is_exit") != null)
                zone.setIsExit((Boolean) body.get("is_exit"));

            // Auto-generate QR and set ACTIVE immediately
            String qrPath = qrGenerationService.generateZoneQr(
                zone.getId(), zone.getFloorId(), zone.getBuildingId());
            zone.setQrCodeUrl(qrPath);
            zone.setStatus(Zone.ZoneStatus.ACTIVE);

            return ResponseEntity.ok(zoneRepository.save(zone));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/zones/{id}")
    public ResponseEntity<?> deleteZone(@PathVariable String id) {
        try {
            if (!zoneRepository.existsById(id))
                return ResponseEntity.notFound().build();
            routeRepository.findByZoneId(id).ifPresent(routeRepository::delete);
            zoneRepository.deleteById(id);
            return ResponseEntity.ok(Map.of("message", "Zone deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/zones/{id}/confirm")
    public ResponseEntity<?> confirmZone(@PathVariable String id) {
        return zoneRepository.findById(id).map(zone -> {
            try {
                String qrPath = qrGenerationService.generateZoneQr(
                    zone.getId(),
                    zone.getFloorId(),
                    zone.getBuildingId()
                );
                zone.setQrCodeUrl(qrPath);
                zone.setStatus(Zone.ZoneStatus.ACTIVE);
                return ResponseEntity.ok(zoneRepository.save(zone));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/zones/{id}/coordinates")
    public ResponseEntity<?> setZoneCoordinates(@PathVariable String id,
                                                  @RequestBody Map<String, Object> body) {
        return zoneRepository.findById(id).map(zone -> {
            try {
                zone.setCoordinates(objectMapper.writeValueAsString(body.get("coordinates")));
                return ResponseEntity.ok(zoneRepository.save(zone));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/zones/{id}/route")
    public ResponseEntity<?> setRoute(@PathVariable String id,
                                       @RequestBody Map<String, Object> body) {
        try {
            Route route = routeRepository.findByZoneId(id).orElse(new Route());
            if (route.getId() == null) route.setId(UUID.randomUUID().toString());
            route.setZoneId(id);
            route.setExitName((String) body.get("exit_name"));
            route.setCoordinatesArray(objectMapper.writeValueAsString(body.get("coordinates")));
            return ResponseEntity.ok(routeRepository.save(route));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/zones/{id}/qr")
    public ResponseEntity<?> downloadQr(@PathVariable String id) {
        return zoneRepository.findById(id).map(zone -> {
            if (zone.getQrCodeUrl() == null)
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "QR not generated. Confirm the zone first."));
            File qrFile = new File(zone.getQrCodeUrl());
            if (!qrFile.exists())
                return ResponseEntity.notFound().build();
            try {
                byte[] bytes = Files.readAllBytes(qrFile.toPath());
                return ResponseEntity.ok()
                    .header("Content-Type", "image/png")
                    .header("Content-Disposition", "attachment; filename=zone-" + id + ".png")
                    .body(bytes);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/zones/{id}/regenerate-qr")
    public ResponseEntity<?> regenerateQr(@PathVariable String id) {
        return zoneRepository.findById(id).map(zone -> {
            try {
                String qrPath = qrGenerationService.generateZoneQr(
                    zone.getId(), zone.getFloorId(), zone.getBuildingId());
                zone.setQrCodeUrl(qrPath);
                return ResponseEntity.ok(zoneRepository.save(zone));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }
}
