package com.evacuation.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String idNumber;
    private String mobileNumber;

    @Convert(converter = User.RoleConverter.class)
    private Role role;

    private String buildingId;

    private String currentZoneId;
    private String currentFloorId;
    private String currentBuildingId;

    private Boolean isActive = true;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Role {
        ADMIN, STUDENT;

        @JsonCreator
        public static Role from(String value) {
            if (value == null) throw new IllegalArgumentException("Role must not be null");
            return Role.valueOf(value.trim().toUpperCase());
        }
    }

    @Converter
    public static class RoleConverter implements AttributeConverter<Role, String> {
        @Override
        public String convertToDatabaseColumn(Role role) {
            return role == null ? null : role.name();
        }

        @Override
        public Role convertToEntityAttribute(String value) {
            if (value == null) return null;
            return Role.valueOf(value.trim().toUpperCase());
        }
    }
}
