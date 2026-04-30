package com.evacuation.controller;

import com.evacuation.model.User;
import com.evacuation.repository.UserRepository;
import com.evacuation.security.JwtUtil;
import com.evacuation.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private EmailService emailService;

    @Value("${admin.code:}")
    private String adminCode;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        try {
            String email = (String) body.get("email");
            String password = (String) body.get("password");
            String roleStr = (String) body.get("role");

            if (userRepository.findByEmail(email).isPresent())
                return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));

            User.Role role = User.Role.from(roleStr);

            if (role == User.Role.ADMIN) {
                String input = (String) body.get("adminCode");
                if (adminCode == null || adminCode.isBlank())
                    return ResponseEntity.badRequest().body(Map.of("error", "Admin code not configured"));
                if (!adminCode.equals(input))
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid admin code"));
            }

            User user = new User();
            user.setId(UUID.randomUUID().toString());
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setFullName((String) body.get("full_name"));
            user.setIdNumber((String) body.get("id_number"));
            user.setMobileNumber((String) body.get("mobile_number"));
            user.setBuildingId((String) body.get("building_id"));
            user.setRole(role);
            user.setIsActive(true);
            userRepository.save(user);

            String token = jwtUtil.generateToken(email, role.name());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                    "id", user.getId(),
                    "email", email,
                    "full_name", user.getFullName() != null ? user.getFullName() : "",
                    "role", role.name(),
                    "building_id", user.getBuildingId() != null ? user.getBuildingId() : ""
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

            if (!passwordEncoder.matches(body.get("password"), user.getPassword()))
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid credentials"));

            if (Boolean.FALSE.equals(user.getIsActive()))
                return ResponseEntity.status(401).body(Map.of("error", "Account is deactivated"));

            String token = jwtUtil.generateToken(email, user.getRole().name());
            return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                    "id", user.getId(),
                    "email", email,
                    "full_name", user.getFullName() != null ? user.getFullName() : "",
                    "role", user.getRole().name(),
                    "building_id", user.getBuildingId() != null ? user.getBuildingId() : ""
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.replace("Bearer ", "");
            String email = jwtUtil.extractEmail(token);
            User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "full_name", user.getFullName() != null ? user.getFullName() : "",
                "role", user.getRole().name(),
                "building_id", user.getBuildingId() != null ? user.getBuildingId() : "",
                "current_zone_id", user.getCurrentZoneId() != null ? user.getCurrentZoneId() : "",
                "current_floor_id", user.getCurrentFloorId() != null ? user.getCurrentFloorId() : "",
                "current_building_id", user.getCurrentBuildingId() != null ? user.getCurrentBuildingId() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(token);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            String link = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(email, link);
        });
        // Always 200 to prevent email enumeration
        return ResponseEntity.ok(Map.of("message", "If the email exists, a reset link was sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        try {
            String token = body.get("token");
            String newPassword = body.get("password");

            User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));

            if (user.getResetTokenExpiry() == null ||
                user.getResetTokenExpiry().isBefore(LocalDateTime.now()))
                return ResponseEntity.badRequest().body(Map.of("error", "Token has expired"));

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
