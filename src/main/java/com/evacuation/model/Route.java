package com.evacuation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "routes")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    private String id;

    private String zoneId;
    private String exitName;

    @Column(columnDefinition = "TEXT")
    private String coordinatesArray;  // JSON array of waypoints
}
