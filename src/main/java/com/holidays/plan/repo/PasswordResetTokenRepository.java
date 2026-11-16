package com.holidays.plan.repo;

import com.holidays.plan.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Consuma il token in modo atomico: vale solo se aggiorna una riga. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    int consume(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :uid and t.usedAt is null")
    int invalidateAll(@Param("uid") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
