package com.tripdiary.photo;

import java.util.Arrays;
import java.util.Optional;

/** 受け付ける画像形式（docs/features/03_photo_upload.md 6章）。DB の CHECK 制約と同じ4種類。 */
public enum PhotoFormat {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    HEIC("image/heic", "heic");

    private final String contentType;
    private final String extension;

    PhotoFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** MIME タイプから形式を求める（完全一致のみ。{@code image/jpg} などの表記揺れは受け付けない）。 */
    public static Optional<PhotoFormat> ofContentType(String contentType) {
        return Arrays.stream(values()).filter(f -> f.contentType.equals(contentType)).findFirst();
    }

    public static Optional<PhotoFormat> ofExtension(String extension) {
        return Arrays.stream(values()).filter(f -> f.extension.equals(extension)).findFirst();
    }
}
