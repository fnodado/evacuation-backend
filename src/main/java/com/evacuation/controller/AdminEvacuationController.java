package com.evacuation.controller;

import com.evacuation.model.*;
import com.evacuation.repository.*;
import com.evacuation.security.JwtUtil;
import com.evacuation.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminEvacuationController {

    @Autowired private EvacuationRepository evacuationRepository;
    @Autowired private EvacuationAckRepository ackRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ZoneRepository zoneRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RouteRepository routeRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private EmailService emailService;
    @Autowired private JwtUtil jwtUtil;

    @GetMapping("/students/locations")
    public ResponseEntity<?> getStudentLocations() {
        List<Map<String, Object>> result = userRepository.findAllCheckedIn().stream()
            .filter(u -> u.getRole() == User.Role.STUDENT)
            .map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getId());
                m.put("full_name", u.getFullName());
                m.put("email", u.getEmail());
                m.put("current_zone_id", u.getCurrentZoneId());
                m.put("current_floor_id", u.getCurrentFloorId());
                m.put("current_building_id", u.getCurrentBuildingId());
                if (u.getCurrentZoneId() != null)
                    zoneRepository.findById(u.getCurrentZoneId())
                        .ifPresent(z -> m.put("zone_name", z.getName()));
                return m;
            }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/evacuate")
    public ResponseEntity<?> triggerEvacuation(
        @RequestHeader("Authorization") String authHeader,
        @RequestBody Map<String, Object> body
    ) {
        try {
            String adminEmail = jwtUtil.extractEmail(authHeader.replace("Bearer ", ""));
            String adminId = userRepository.findByEmail(adminEmail)
                .map(User::getId).orElse("unknown");

            String type = (String) body.get("type");
            String message = (String) body.get("message");

            @SuppressWarnings("unchecked")
            Map<String, String> scope = (Map<String, String>) body.get("scope");

            String scopeType;
            String scopeId;
            if (scope.containsKey("zoneId")) {
                scopeType = "ZONE"; scopeId = scope.get("zoneId");
            } else if (scope.containsKey("floorId")) {
                scopeType = "FLOOR"; scopeId = scope.get("floorId");
            } else if (scope.containsKey("buildingId")) {
                scopeType = "BUILDING"; scopeId = scope.get("buildingId");
            } else {
                scopeType = "ALL"; scopeId = "ALL";
            }

            // Identify affected students
            List<User> students = switch (scopeType) {
                case "ZONE" -> userRepository.findByCurrentZone(scopeId);
                case "FLOOR" -> userRepository.findByCurrentFloor(scopeId);
                case "BUILDING" -> userRepository.findByCurrentBuilding(scopeId);
                default -> userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.STUDENT
                        && Boolean.TRUE.equals(u.getIsActive()))
                    .collect(Collectors.toList());
            };
            students = students.stream()
                .filter(u -> u.getRole() == User.Role.STUDENT)
                .collect(Collectors.toList());

            // Create Evacuation record
            Evacuation evac = new Evacuation();
            evac.setId(UUID.randomUUID().toString());
            evac.setType(type);
            evac.setScopeType(scopeType);
            evac.setScopeId(scopeId);
            evac.setMessage(message);
            evac.setTriggeredBy(adminId);
            evac.setStatus(Evacuation.EvacuationStatus.ACTIVE);
            evacuationRepository.save(evac);

            // Pre-create ack records for all affected students
            ackRepository.saveAll(students.stream().map(s -> {
                EvacuationAck ack = new EvacuationAck();
                ack.setId(UUID.randomUUID().toString());
                ack.setEvacuationId(evac.getId());
                ack.setStudentId(s.getId());
                return ack;
            }).collect(Collectors.toList()));

            // Resolve building id for WebSocket topic
            String buildingId = scopeId;
            if ("ZONE".equals(scopeType))
                buildingId = zoneRepository.findById(scopeId)
                    .map(Zone::getBuildingId).orElse(scopeId);

            String buildingName = buildingRepository.findById(buildingId)
                .map(Building::getName).orElse("the building");

            // WebSocket broadcast
            Map<String, Object> wsPayload = new LinkedHashMap<>();
            wsPayload.put("evacuationId", evac.getId());
            wsPayload.put("type", type);
            wsPayload.put("message", message != null ? message : "");
            wsPayload.put("scopeType", scopeType);
            wsPayload.put("scopeId", scopeId);
            messagingTemplate.convertAndSend("/topic/evacuate/" + buildingId, wsPayload);

            // Async: email + SMS per student
            for (User student : students) {
                String zoneName = student.getCurrentZoneId() != null
                    ? zoneRepository.findById(student.getCurrentZoneId())
                        .map(Zone::getName).orElse("your zone")
                    : "your zone";

                String exitRoute = student.getCurrentZoneId() != null
                    ? routeRepository.findByZoneId(student.getCurrentZoneId())
                        .map(Route::getExitName).orElse("nearest exit")
                    : "nearest exit";

                emailService.sendEvacuationAlert(
                    student.getEmail(), student.getFullName(),
                    zoneName, buildingName, type, exitRoute);
                logNotification(student.getId(), evac.getId(), "EMAIL");
                logNotification(student.getId(), evac.getId(), "WEBSOCKET");
            }

            return ResponseEntity.ok(Map.of(
                "evacuationId", evac.getId(),
                "studentsAlerted", students.size(),
                "status", "ACTIVE"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/evacuate/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable String id) {
        return evacuationRepository.findById(id).map(evac -> {
            List<EvacuationAck> acks = ackRepository.findByEvacuationId(id);
            long acked = acks.stream().filter(a -> a.getAcknowledgedAt() != null).count();
            long safe = acks.stream().filter(a -> a.getSafeAt() != null).count();
            int total = acks.size();

            return ResponseEntity.ok(Map.of(
                "evacuationId", id,
                "status", evac.getStatus(),
                "type", evac.getType(),
                "triggeredAt", evac.getTriggeredAt(),
                "totalAlerted", total,
                "acknowledged", acked,
                "safe", safe,
                "nonResponsive", total - acked,
                "acks", acks
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/evacuate/{id}/end")
    public ResponseEntity<?> endEvacuation(@PathVariable String id) {
        return evacuationRepository.findById(id).map(evac -> {
            evac.setStatus(Evacuation.EvacuationStatus.COMPLETED);
            evac.setEndedAt(LocalDateTime.now());
            evacuationRepository.save(evac);

            String buildingName = buildingRepository.findById(evac.getScopeId() != null ? evac.getScopeId() : "")
                .map(Building::getName).orElse("the building");

            // All-clear: WebSocket + email + SMS
            ackRepository.findByEvacuationId(id).forEach(ack ->
                userRepository.findById(ack.getStudentId()).ifPresent(student -> {
                    messagingTemplate.convertAndSend(
                        "/topic/evacuate/" + evac.getScopeId(),
                        Map.of("type", "ALL_CLEAR", "evacuationId", id, "buildingName", buildingName)
                    );
                    emailService.sendAllClearEmail(
                        student.getEmail(), student.getFullName(), buildingName);
                })
            );

            return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "endedAt", evac.getEndedAt()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/evacuate/{id}/alerts")
    public ResponseEntity<?> getAlerts(@PathVariable String id) {
        return ResponseEntity.ok(notificationRepository.findByEvacuationId(id));
    }

    @GetMapping("/evacuations/history")
    public ResponseEntity<?> getHistory() {
        return ResponseEntity.ok(evacuationRepository.findAllByOrderByTriggeredAtDesc());
    }

    @GetMapping("/notifications")
    public ResponseEntity<?> getAllNotifications() {
        return ResponseEntity.ok(notificationRepository.findAll());
    }

    private void logNotification(String studentId, String evacuationId, String channel) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID().toString());
        n.setStudentId(studentId);
        n.setEvacuationId(evacuationId);
        n.setChannel(channel);
        n.setDeliveryStatus("SENT");
        notificationRepository.save(n);
    }
}
