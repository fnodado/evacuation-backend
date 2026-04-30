package com.evacuation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "floors")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Floor {

    @Id
    private String id;

    private String buildingId;
    private Integer floorNumber;
    private String label;
    private String blueprintUrl;
}
