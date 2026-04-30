package com.evacuation.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "buildings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Building {
    @Id
    private String id;
    private String name;
    private String address;
    private Integer totalCapacity;
    private String blueprintUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
