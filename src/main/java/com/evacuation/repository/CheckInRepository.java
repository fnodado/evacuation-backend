package com.evacuation.repository;

import com.evacuation.model.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CheckInRepository extends JpaRepository<CheckIn, String> {
    List<CheckIn> findByStudentIdOrderByCheckinTimestampDesc(String studentId);
    List<CheckIn> findByZoneId(String zoneId);
    Optional<CheckIn> findTopByStudentIdOrderByCheckinTimestampDesc(String studentId);
    void deleteByStudentId(String studentId);
}
