package com.evacuation.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "evacuations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Evacuation {

    @Id
    private String id;

    private String type;        // EARTHQUAKE, FIRE, FLOOD, OTHER
    private String scopeType;   // BUILDING, FLOOR, ZONE, ALL
    private String scopeId;
    private String message;
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    private EvacuationStatus status = EvacuationStatus.ACTIVE;

    @CreationTimestamp
    private LocalDateTime triggeredAt;

    private LocalDateTime endedAt;

    public enum EvacuationStatus {
        ACTIVE, COMPLETED
    }
}
