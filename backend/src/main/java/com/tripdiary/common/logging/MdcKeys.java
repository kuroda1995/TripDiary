package com.tripdiary.common.logging;

/** ログの MDC に設定するキー（docs/rules.md 5.3）。 */
public final class MdcKeys {

    /** リクエスト単位の追跡ID。 */
    public static final String REQUEST_ID = "requestId";

    /** 認証済みユーザーID（JWT 認証成功後に設定する）。 */
    public static final String USER_ID = "userId";

    private MdcKeys() {}
}
