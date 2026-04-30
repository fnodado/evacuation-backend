package com.evacuation.repository;

import com.evacuation.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, String> {
    List<Zone> findByBuildingId(String buildingId);
    List<Zone> findByFloorId(String floorId);
    void deleteByBuildingId(String buildingId);
    List<Zone> findByBuildingIdAndStatus(String buildingId, Zone.ZoneStatus status);
}
