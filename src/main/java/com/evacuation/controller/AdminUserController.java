package com.evacuation.controller;

import com.evacuation.model.User;
import com.evacuation.repository.CheckInRepository;
import com.evacuation.repository.EvacuationAckRepository;
import com.evacuation.repository.NotificationRepository;
import com.evacuation.repository.UserRepository;
import com.evacuation.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    @Autowired private UserRepository userRepository;
    @Autowired private CheckInRepository checkInRepository;
    @Autowired private EvacuationAckRepository ackRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @GetMapping
    public ResponseEntity<?> getUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String buildingId,
        @RequestParam(required = false) Boolean isActive,
        @RequestParam(required = false) String search
    ) {
        List<User> users = userRepository.findAll();

        if (role != null && !role.isBlank())
            users = users.stream()
                .filter(u -> u.getRole() != null && u.getRole().name().equalsIgnoreCase(role))
                .collect(Collectors.toList());

        if (buildingId != null && !buildingId.isBlank())
            users = users.stream()
                .filter(u -> buildingId.equals(u.getBuildingId()))
                .collect(Collectors.toList());

        if (isActive != null)
            users = users.stream()
                .filter(u -> isActive.equals(u.getIsActive()))
                .collect(Collectors.toList());

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            users = users.stream().filter(u ->
                (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                (u.getIdNumber() != null && u.getIdNumber().toLowerCase().contains(q))
            ).collect(Collectors.toList());
        }

        int total = users.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        List<User> paged = start < total ? users.subList(start, end) : Collections.emptyList();

        return ResponseEntity.ok(Map.of(
            "content", paged.stream().map(this::toMap).collect(Collectors.toList()),
            "totalElements", total,
            "totalPages", (int) Math.ceil((double) total / size),
            "page", page
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        return userRepository.findById(id)
            .map(u -> ResponseEntity.ok(toMap(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        try {
            String email = (String) body.get("email");
            if (userRepository.findByEmail(email).isPresent())
                return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));

            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode((String) body.get("password")));
            user.setFullName((String) body.get("full_name"));
            user.setIdNumber((String) body.get("id_number"));
            user.setMobileNumber((String) body.get("mobile_number"));
            user.setBuildingId((String) body.get("building_id"));
            user.setRole(User.Role.from((String) body.get("role")));
            user.setIsActive(true);
            return ResponseEntity.ok(toMap(userRepository.save(user)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id,
                                         @RequestBody Map<String, Object> body) {
        return userRepository.findById(id).map(user -> {
            if (body.containsKey("full_name")) user.setFullName((String) body.get("full_name"));
            if (body.containsKey("mobile_number")) user.setMobileNumber((String) body.get("mobile_number"));
            if (body.containsKey("building_id")) user.setBuildingId((String) body.get("building_id"));
            if (body.containsKey("role")) user.setRole(User.Role.from((String) body.get("role")));
            return ResponseEntity.ok(toMap(userRepository.save(user)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> toggleStatus(@PathVariable String id,
                                           @RequestBody Map<String, Boolean> body) {
        return userRepository.findById(id).map(user -> {
            Boolean active = body.get("isActive");
            user.setIsActive(active != null ? active : !Boolean.FALSE.equals(user.getIsActive()));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("isActive", user.getIsActive()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id))
            return ResponseEntity.notFound().build();
        checkInRepository.deleteByStudentId(id);
        ackRepository.deleteByStudentId(id);
        notificationRepository.deleteByStudentId(id);
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable String id) {
        return userRepository.findById(id).map(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            String link = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), link);
            return ResponseEntity.ok(Map.of("message", "Reset email sent to " + user.getEmail()));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/checkins")
    public ResponseEntity<?> getCheckins(@PathVariable String id) {
        return ResponseEntity.ok(
            checkInRepository.findByStudentIdOrderByCheckinTimestampDesc(id));
    }

    @GetMapping("/{id}/evacuation-history")
    public ResponseEntity<?> getEvacuationHistory(@PathVariable String id) {
        return ResponseEntity.ok(ackRepository.findByStudentId(id));
    }

    private Map<String, Object> toMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("full_name", u.getFullName());
        m.put("id_number", u.getIdNumber());
        m.put("mobile_number", u.getMobileNumber());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("building_id", u.getBuildingId());
        m.put("current_zone_id", u.getCurrentZoneId());
        m.put("current_floor_id", u.getCurrentFloorId());
        m.put("current_building_id", u.getCurrentBuildingId());
        m.put("is_active", u.getIsActive());
        m.put("created_at", u.getCreatedAt());
        return m;
    }
}
