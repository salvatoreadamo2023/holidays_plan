package com.holidays.plan.repo;

import com.holidays.plan.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :uid and n.readAt is null")
    int markAllRead(@Param("uid") Long userId, @Param("at") Instant at);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.userId = :uid and n.id = :id and n.readAt is null")
    int markRead(@Param("uid") Long userId, @Param("id") Long id, @Param("at") Instant at);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
