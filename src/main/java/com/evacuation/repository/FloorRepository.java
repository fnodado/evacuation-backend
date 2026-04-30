package com.evacuation.repository;

import com.evacuation.model.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FloorRepository extends JpaRepository<Floor, String> {
    List<Floor> findByBuildingId(String buildingId);
}
