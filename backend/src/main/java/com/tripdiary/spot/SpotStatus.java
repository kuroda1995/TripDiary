package com.tripdiary.spot;

/**
 * 自分用リストのステータス（docs/features/08_spot_status.md 1章。本人だけが見られる）。
 *
 * <p>一覧・詳細に閲覧者のステータスを載せるため spot パッケージに置く（読み書きは status パッケージ）。
 */
public enum SpotStatus {
    /** 訪問済み */
    VISITED,
    /** 行きたい */
    WANT_TO_GO
}
