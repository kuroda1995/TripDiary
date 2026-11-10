package com.tripdiary.photo;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * アップロードの用途。用途ごとに S3 キーのプレフィックスとサイズ上限が決まる。
 *
 * <p>S3 キーは {@code {prefix}/{userId}/{UUID}.{拡張子}}。ファイル名にユーザー入力は使わない。
 */
public enum UploadPurpose {
    /** スポットの写真（1枚 10MB まで）。 */
    SPOT_PHOTO("spots", 10L * 1024 * 1024),
    /** ユーザーのアイコン（5MB まで）。保存はプロフィール編集（AUTH-07）で行う。 */
    AVATAR("avatars", 5L * 1024 * 1024);

    private final String prefix;
    private final long maxBytes;

    UploadPurpose(String prefix, long maxBytes) {
        this.prefix = prefix;
        this.maxBytes = maxBytes;
    }

    public String prefix() {
        return prefix;
    }

    public long maxBytes() {
        return maxBytes;
    }

    private static final Pattern FILE_NAME =
            Pattern.compile(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(\\w+)");

    /** このユーザーのファイルを置くプレフィックス（例: {@code spots/42/}）。 */
    public String userPrefix(long userId) {
        return prefix + "/" + userId + "/";
    }

    /** 新しいファイルのキーを作る。 */
    public String newKey(long userId, PhotoFormat format) {
        return userPrefix(userId) + UUID.randomUUID() + "." + format.extension();
    }

    /** このユーザーがこの用途で発行したキーの形式か（他人のキー・別用途のキー・不正な形式を弾く）。 */
    public boolean isOwnKey(String key, long userId) {
        if (key == null || !key.startsWith(userPrefix(userId))) {
            return false;
        }
        var matcher = FILE_NAME.matcher(key.substring(userPrefix(userId).length()));
        return matcher.matches() && PhotoFormat.ofExtension(matcher.group(1)).isPresent();
    }
}
