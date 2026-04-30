package com.evacuation.controller;

import com.evacuation.model.*;
import com.evacuation.repository.*;
import com.evacuation.security.JwtUtil;
import com.evacuation.service.QrGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/student")
@CrossOrigin(origins = "*")
public class StudentController {

    @Autowired private UserRepository userRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private CheckInRepository checkInRepository;
    @Autowired private EvacuationRepository evacuationRepository;
    @Autowired private EvacuationAckRepository ackRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private QrGenerationService qrGenerationService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private User getStudent(String authHeader) {
        String email = jwtUtil.extractEmail(authHeader.replace("Bearer ", ""));
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ── QR CHECK-IN (Flow 5) ───────────────────────────────────────────────────

    @PostMapping("/checkin")
    public ResponseEntity<?> checkIn(@RequestHeader("Authorization") String auth,
                                      @RequestBody Map<String, String> body) {
        try {
            User student = getStudent(auth);
            String zoneId     = body.get("zoneId");
            String floorId    = body.get("floorId");
            String buildingId = body.get("buildingId");
            String sig        = body.get("sig");

            if (!qrGenerationService.validateSignature(zoneId, floorId, buildingId, sig))
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid QR signature"));

            Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new RuntimeException("Zone not found"));

            if (zone.getStatus() != Zone.ZoneStatus.ACTIVE)
                return ResponseEntity.badRequest().body(Map.of("error", "Zone is not active"));

            // Update student location
            student.setCurrentZoneId(zoneId);
            student.setCurrentFloorId(floorId);
            student.setCurrentBuildingId(buildingId);
            userRepository.save(student);

            // Log check-in
            CheckIn checkIn = new CheckIn();
            checkIn.setId(UUID.randomUUID().toString());
            checkIn.setStudentId(student.getId());
            checkIn.setZoneId(zoneId);
            checkIn.setFloorId(floorId);
            checkIn.setBuildingId(buildingId);
            checkInRepository.save(checkIn);

            // Push occupancy update to admin dashboard
            messagingTemplate.convertAndSend("/topic/occupancy/" + buildingId,
                Map.of("studentId", student.getId(), "zoneId", zoneId, "action", "CHECKIN"));

            // Auto-safe if assembly point during active evacuation
            if (Boolean.TRUE.equals(zone.getIsAssemblyPoint())) {
                evacuationRepository
                    .findByStatusOrderByTriggeredAtDesc(Evacuation.EvacuationStatus.ACTIVE)
                    .stream().findFirst()
                    .ifPresent(evac ->
                        ackRepository.findByEvacuationIdAndStudentId(evac.getId(), student.getId())
                            .ifPresent(ack -> {
                                if (ack.getSafeAt() == null) {
                                    ack.setSafeAt(LocalDateTime.now());
                                    ack.setSafeMethod("QR");
                                    ackRepository.save(ack);
                                    messagingTemplate.convertAndSend(
                                        "/topic/evacuation-status/" + evac.getId(),
                                        Map.of("studentId", student.getId(), "action", "SAFE_QR")
                                    );
                                }
                            })
                    );
            }

            return ResponseEntity.ok(Map.of(
                "message", "Checked in to " + zone.getName(),
                "zone", zone.getName(),
                "floor_id", floorId != null ? floorId : "",
                "building_id", buildingId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── MANUAL LOCATION UPDATE (Flow 6) ───────────────────────────────────────

    @PutMapping("/location")
    public ResponseEntity<?> updateLocation(@RequestHeader("Authorization") String auth,
                                             @RequestBody Map<String, String> body) {
        try {
            User student = getStudent(auth);
            String zoneId     = body.get("zoneId");
            String floorId    = body.get("floorId");
            String buildingId = body.get("buildingId");

            Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new RuntimeException("Zone not found"));

            student.setCurrentZoneId(zoneId);
            student.setCurrentFloorId(floorId);
            student.setCurrentBuildingId(buildingId);
            userRepository.save(student);

            CheckIn checkIn = new CheckIn();
            checkIn.setId(UUID.randomUUID().toString());
            checkIn.setStudentId(student.getId());
            checkIn.setZoneId(zoneId);
            checkIn.setFloorId(floorId);
            checkIn.setBuildingId(buildingId);
            checkInRepository.save(checkIn);

            messagingTemplate.convertAndSend("/topic/occupancy/" + buildingId,
                Map.of("studentId", student.getId(), "zoneId", zoneId, "action", "CHECKIN"));

            return ResponseEntity.ok(Map.of("message", "Location updated to " + zone.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evacuation-route")
    public ResponseEntity<?> getEvacuationRoute(@RequestHeader("Authorization") String auth) {
        User student = getStudent(auth);
        if (student.getCurrentZoneId() == null)
            return ResponseEntity.badRequest().body(Map.of("error", "Not checked in to any zone"));

        return routeRepository.findByZoneId(student.getCurrentZoneId())
            .map(r -> ResponseEntity.ok((Object) Map.of(
                "zone_id", r.getZoneId(),
                "exit_name", r.getExitName(),
                "coordinates", r.getCoordinatesArray() != null ? r.getCoordinatesArray() : "[]"
            )))
            .orElse(ResponseEntity.ok(Map.of("message", "No route assigned for this zone")));
    }

    @GetMapping("/blueprint/{floorId}")
    public ResponseEntity<?> getBlueprint(@PathVariable String floorId) {
        return ResponseEntity.ok(Map.of(
            "floor_id", floorId,
            "zones", zoneRepository.findByFloorId(floorId)
        ));
    }

    // ── EVACUATION ACK / SAFE (Flow 8) ────────────────────────────────────────

    @PostMapping("/evacuate/{evacuationId}/acknowledge")
    public ResponseEntity<?> acknowledge(@PathVariable String evacuationId,
                                          @RequestHeader("Authorization") String auth) {
        try {
            User student = getStudent(auth);
            return ackRepository
                .findByEvacuationIdAndStudentId(evacuationId, student.getId())
                .map(ack -> {
                    if (ack.getAcknowledgedAt() == null) {
                        ack.setAcknowledgedAt(LocalDateTime.now());
                        ackRepository.save(ack);
                    }
                    messagingTemplate.convertAndSend(
                        "/topic/evacuation-status/" + evacuationId,
                        Map.of("studentId", student.getId(), "action", "ACKNOWLEDGED"));
                    return ResponseEntity.ok(Map.of("message", "Acknowledged"));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/evacuate/{evacuationId}/safe")
    public ResponseEntity<?> markSafe(@PathVariable String evacuationId,
                                       @RequestHeader("Authorization") String auth) {
        try {
            User student = getStudent(auth);
            return ackRepository
                .findByEvacuationIdAndStudentId(evacuationId, student.getId())
                .map(ack -> {
                    if (ack.getSafeAt() == null) {
                        ack.setSafeAt(LocalDateTime.now());
                        ack.setSafeMethod("BUTTON");
                        ackRepository.save(ack);
                    }
                    messagingTemplate.convertAndSend(
                        "/topic/evacuation-status/" + evacuationId,
                        Map.of("studentId", student.getId(), "action", "SAFE"));
                    return ResponseEntity.ok(Map.of("message", "Marked as safe"));
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── ACTIVE EVACUATION CHECK ───────────────────────────────────────────────

    @GetMapping("/evacuation/active")
    public ResponseEntity<?> getActiveEvacuation(@RequestHeader("Authorization") String auth) {
        try {
            User student = getStudent(auth);
            List<Evacuation> actives = evacuationRepository
                .findByStatusOrderByTriggeredAtDesc(Evacuation.EvacuationStatus.ACTIVE);
            if (actives.isEmpty()) {
                return ResponseEntity.ok(Map.of("active", false));
            }
            Evacuation evac = actives.get(0);
            Optional<EvacuationAck> ack = ackRepository
                .findByEvacuationIdAndStudentId(evac.getId(), student.getId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("active", true);
            result.put("evacuation_id", evac.getId());
            result.put("type", evac.getType());
            result.put("message", evac.getMessage() != null ? evac.getMessage() : "");
            result.put("triggered_at", evac.getTriggeredAt());
            result.put("acknowledged", ack.map(a -> a.getAcknowledgedAt() != null).orElse(false));
            result.put("safe", ack.map(a -> a.getSafeAt() != null).orElse(false));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── PROFILE & HISTORY ─────────────────────────────────────────────────────

    @GetMapping("/checkins")
    public ResponseEntity<?> getMyCheckins(@RequestHeader("Authorization") String auth) {
        User student = getStudent(auth);
        return ResponseEntity.ok(
            checkInRepository.findByStudentIdOrderByCheckinTimestampDesc(student.getId()));
    }

    @GetMapping("/evacuations/history")
    public ResponseEntity<?> getMyEvacuationHistory(@RequestHeader("Authorization") String auth) {
        User student = getStudent(auth);
        return ResponseEntity.ok(ackRepository.findByStudentId(student.getId()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getMyNotifications(@RequestHeader("Authorization") String auth) {
        User student = getStudent(auth);
        return ResponseEntity.ok(notificationRepository.findByStudentId(student.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestHeader("Authorization") String auth,
                                            @RequestBody Map<String, Object> body) {
        try {
            User student = getStudent(auth);
            if (body.containsKey("full_name")) student.setFullName((String) body.get("full_name"));
            if (body.containsKey("mobile_number")) student.setMobileNumber((String) body.get("mobile_number"));
            userRepository.save(student);
            return ResponseEntity.ok(Map.of("message", "Profile updated"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestHeader("Authorization") String auth,
                                             @RequestBody Map<String, String> body) {
        try {
            User student = getStudent(auth);
            if (!passwordEncoder.matches(body.get("currentPassword"), student.getPassword()))
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
            student.setPassword(passwordEncoder.encode(body.get("newPassword")));
            userRepository.save(student);
            return ResponseEntity.ok(Map.of("message", "Password changed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
