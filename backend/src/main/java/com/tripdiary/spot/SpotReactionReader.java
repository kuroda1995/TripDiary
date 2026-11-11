package com.tripdiary.spot;

import java.util.Collection;
import java.util.Map;

/**
 * スポットへの反応（行ってみたい！・コメント）をまとめて読む。
 *
 * <p>一覧では件数に比例しない回数（1回）で取得すること（実装: {@link JdbcSpotReactionReader}）。
 */
public interface SpotReactionReader {

    /**
     * @param spotIds 対象のスポット
     * @param viewerId 閲覧者（未ログインなら null）
     * @return スポット ID ごとの反応（含まれないスポットは呼び出し側で {@link SpotReactions#NONE} とする）
     */
    Map<Long, SpotReactions> read(Collection<Long> spotIds, Long viewerId);
}
