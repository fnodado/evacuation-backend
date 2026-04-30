package com.evacuation.repository;

import com.evacuation.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetToken(String resetToken);
    List<User> findByRole(User.Role role);
    List<User> findByBuildingId(String buildingId);

    @Query("SELECT u FROM User u WHERE u.currentZoneId IS NOT NULL AND u.isActive = true")
    List<User> findAllCheckedIn();

    @Query("SELECT u FROM User u WHERE u.currentBuildingId = :buildingId AND u.isActive = true")
    List<User> findByCurrentBuilding(@Param("buildingId") String buildingId);

    @Query("SELECT u FROM User u WHERE u.currentFloorId = :floorId AND u.isActive = true")
    List<User> findByCurrentFloor(@Param("floorId") String floorId);

    @Query("SELECT u FROM User u WHERE u.currentZoneId = :zoneId AND u.isActive = true")
    List<User> findByCurrentZone(@Param("zoneId") String zoneId);
}
