package com.holidays.plan.repo;

import com.holidays.plan.domain.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token")
    int deleteByToken(@Param("token") String token);
}
