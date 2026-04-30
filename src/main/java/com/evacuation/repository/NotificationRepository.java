package com.evacuation.repository;

import com.evacuation.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByEvacuationId(String evacuationId);
    List<Notification> findByStudentId(String studentId);
    void deleteByStudentId(String studentId);
}
