package com.evacuation.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    private String studentId;
    private String evacuationId;
    private String channel;         // EMAIL, SMS, WEBSOCKET
    private String deliveryStatus;  // SENT, FAILED

    @CreationTimestamp
    private LocalDateTime sentAt;
}
