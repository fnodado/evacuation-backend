package com.evacuation.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

// Transient prediction fields (not persisted, used by PredictionService and earthquake flow)

@Entity
@Table(name = "zones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Zone {

    @Id
    private String id;

    private String buildingId;
    private String floorId;
    private String name;
    private String zoneType;
    private Integer maxCapacity;
    private String position;
    private Boolean isExit = false;
    private Boolean isAssemblyPoint = false;

    @Enumerated(EnumType.STRING)
    private ZoneStatus status = ZoneStatus.PENDING;

    private String qrCodeUrl;

    @Column(columnDefinition = "TEXT")
    private String coordinates;

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Transient fields used only by PredictionService — not stored in DB
    @Transient private Integer peopleCount;
    @Transient private String movementSpeed;
    @Transient private String timeOfDay;
    @Transient private Boolean emergencyFlag;

    public enum ZoneStatus {
        PENDING, CONFIRMED, ACTIVE
    }
}
