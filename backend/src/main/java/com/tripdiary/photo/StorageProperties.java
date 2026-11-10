package com.tripdiary.photo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 写真ストレージ（S3）の設定（docs/features/03_photo_upload.md）。
 *
 * @param bucket バケット名
 * @param region リージョン
 * @param endpoint S3 互換サーバーの URL（ローカル・テストの S3Mock 用）。空なら AWS の S3 に接続する
 * @param accessKey {@code endpoint} 指定時のみ使うアクセスキー（本番は IAM ロールの認証情報を使う）
 * @param secretKey {@code endpoint} 指定時のみ使うシークレットキー
 * @param publicBaseUrl 画像の配信 URL の基点（本番は CloudFront のドメイン）。{@code publicBaseUrl + "/" + s3Key}
 *     で配信する
 * @param presignTtl 署名付き URL の有効期間
 * @param orphanRetention 投稿に紐付かない写真ファイルを残しておく期間
 */
@Validated
@ConfigurationProperties("app.storage")
public record StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        String endpoint,
        String accessKey,
        String secretKey,
        @NotBlank String publicBaseUrl,
        @NotNull Duration presignTtl,
        @NotNull Duration orphanRetention) {

    public boolean hasCustomEndpoint() {
        return StringUtils.hasText(endpoint);
    }
}
