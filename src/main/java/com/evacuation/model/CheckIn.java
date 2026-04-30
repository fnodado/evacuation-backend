package com.evacuation.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckIn {

    @Id
    private String id;

    private String studentId;
    private String zoneId;
    private String floorId;
    private String buildingId;

    @CreationTimestamp
    private LocalDateTime checkinTimestamp;
}
