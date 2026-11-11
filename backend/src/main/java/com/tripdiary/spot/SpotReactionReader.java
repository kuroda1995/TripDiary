package com.tripdiary.spot;

import java.util.Collection;
import java.util.Map;

/**
 * スポットへの反応をまとめて読む（実装は like パッケージ）。
 *
 * <p>spot パッケージが like パッケージに依存しないよう、インターフェースをこちらに置く。 一覧では件数に比例しない回数（1回）で取得すること。
 */
public interface SpotReactionReader {

    /**
     * @param spotIds 対象のスポット
     * @param viewerId 閲覧者（未ログインなら null）
     * @return スポット ID ごとの反応（反応が無いスポットは含めなくてよい。呼び出し側で {@link SpotReactions#NONE} とする）
     */
    Map<Long, SpotReactions> read(Collection<Long> spotIds, Long viewerId);
}
