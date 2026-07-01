package com.project.picngo.notification.repository;

import com.project.picngo.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM notification WHERE created_at < :cutoff LIMIT :limit", nativeQuery = true)
    int deleteByCreatedAtBeforeWithLimit(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
