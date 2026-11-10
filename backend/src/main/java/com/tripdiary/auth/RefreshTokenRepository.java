package com.tripdiary.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** ユーザーの有効なリフレッシュトークンをすべて失効させる。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.user.id = :userId and t.revokedAt is null")
    int revokeAllByUserId(@Param("userId") long userId, @Param("now") Instant now);
}
