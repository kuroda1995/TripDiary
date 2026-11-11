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

    /**
     * ユーザーのトークンのうち、{@code since} 以降にローテーションで使用済みになったもの（猶予期間中で、まだ再利用できるもの）を失効させる。
     * ログアウト後に古いトークンで再びログインできないようにする。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.user.id = :userId and t.revokedAt is null"
                    + " and t.rotatedAt is not null and t.rotatedAt >= :since")
    int revokeRecentlyRotated(
            @Param("userId") long userId, @Param("since") Instant since, @Param("now") Instant now);
}
