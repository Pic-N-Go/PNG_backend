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

    // 일일이 하나씩 읽음 처리하지 않고, UPDATE 쿼리 한 방으로 내 알림을 전부 '읽음(isRead = true)' 상태로 변경
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    // 특정 날짜 이전의 알림을 삭제하는 메서드
    // 30일 이상 지난 알림을 삭제할 때 사용
    // 1000건씩 삭제하도록 제한을 두어 한 번에 너무 많은 데이터를 삭제하지 않도록 함
    // 장시간 DB락 방지를 위해 tractional과 modifying 어노테이션을 붙임
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM notification WHERE created_at < :cutoff LIMIT :limit", nativeQuery = true)
    int deleteByCreatedAtBeforeWithLimit(@Param("cutoff") LocalDateTime cutoff, @Param("limit") int limit);
}
