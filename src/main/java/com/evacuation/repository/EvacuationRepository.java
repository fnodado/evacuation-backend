package com.evacuation.repository;

import com.evacuation.model.Evacuation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvacuationRepository extends JpaRepository<Evacuation, String> {
    List<Evacuation> findByStatusOrderByTriggeredAtDesc(Evacuation.EvacuationStatus status);
    List<Evacuation> findAllByOrderByTriggeredAtDesc();
}
