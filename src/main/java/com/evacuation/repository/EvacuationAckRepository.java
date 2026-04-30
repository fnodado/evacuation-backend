package com.evacuation.repository;

import com.evacuation.model.EvacuationAck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvacuationAckRepository extends JpaRepository<EvacuationAck, String> {
    List<EvacuationAck> findByEvacuationId(String evacuationId);
    List<EvacuationAck> findByStudentId(String studentId);
    Optional<EvacuationAck> findByEvacuationIdAndStudentId(String evacuationId, String studentId);
    long countByEvacuationIdAndAcknowledgedAtIsNotNull(String evacuationId);
    long countByEvacuationIdAndSafeAtIsNotNull(String evacuationId);
    void deleteByStudentId(String studentId);
}
