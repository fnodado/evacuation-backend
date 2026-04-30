package com.evacuation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "evacuation_acks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvacuationAck {

    @Id
    private String id;

    private String evacuationId;
    private String studentId;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime safeAt;
    private String safeMethod;  // BUTTON or QR
}
