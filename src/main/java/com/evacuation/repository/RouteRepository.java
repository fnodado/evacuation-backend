package com.evacuation.repository;

import com.evacuation.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, String> {
    Optional<Route> findByZoneId(String zoneId);
}
