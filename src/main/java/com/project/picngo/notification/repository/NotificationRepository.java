package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserId(Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}
