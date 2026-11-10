package com.tripdiary.photo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * 写真ファイルの保存先（S3）の操作。
 *
 * <p>SDK の例外は {@link StorageException}（500 STORAGE_ERROR）に変換する。 署名付き URL
 * は認証情報と同等に扱い、ログに出さない（docs/features/03_photo_upload.md 9章）。
 */
@Component
@RequiredArgsConstructor
public class PhotoStorage {

    /** DeleteObjects で一度に削除できる上限。 */
    static final int DELETE_BATCH_SIZE = 1000;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    /** 署名付き PUT URL。 */
    public record PresignedUpload(String url, Instant expiresAt) {}

    /** S3 上のファイルの情報。 */
    public record StoredObject(String contentType, long size) {}

    /** S3 上のファイル（一覧取得の結果）。 */
    public record ListedObject(String key, Instant lastModified) {}

    /**
     * 署名付き PUT URL を発行する。
     *
     * <p>Content-Type と Content-Length を署名に含めるため、指定と異なる形式・サイズのアップロードは S3 側で拒否される。
     */
    public PresignedUpload presignPut(String key, String contentType, long contentLength) {
        try {
            PresignedPutObjectRequest presigned =
                    s3Presigner.presignPutObject(
                            r ->
                                    r.signatureDuration(properties.presignTtl())
                                            .putObjectRequest(
                                                    PutObjectRequest.builder()
                                                            .bucket(properties.bucket())
                                                            .key(key)
                                                            .contentType(contentType)
                                                            .contentLength(contentLength)
                                                            .build()));
            return new PresignedUpload(presigned.url().toString(), presigned.expiration());
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    /** ファイルの情報を返す。存在しなければ空。 */
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head =
                    s3Client.headObject(r -> r.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentType(), head.contentLength()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw new StorageException(e);
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    /**
     * ファイルをまとめて削除する（存在しないキーは無視される）。
     *
     * @return 削除に失敗したキーの数
     */
    public int deleteAll(Collection<String> keys) {
        List<String> all = List.copyOf(keys);
        int failed = 0;
        for (int from = 0; from < all.size(); from += DELETE_BATCH_SIZE) {
            List<ObjectIdentifier> batch =
                    all.subList(from, Math.min(from + DELETE_BATCH_SIZE, all.size())).stream()
                            .map(key -> ObjectIdentifier.builder().key(key).build())
                            .toList();
            try {
                DeleteObjectsResponse response =
                        s3Client.deleteObjects(
                                r ->
                                        r.bucket(properties.bucket())
                                                .delete(
                                                        Delete.builder()
                                                                .objects(batch)
                                                                .quiet(true)
                                                                .build()));
                failed += response.errors().size();
            } catch (SdkException e) {
                throw new StorageException(e);
            }
        }
        return failed;
    }

    /** プレフィックス配下のファイルを、1ページ（最大1000件）ずつ渡す。 */
    public void listPages(String prefix, Consumer<List<ListedObject>> pageConsumer) {
        try {
            for (ListObjectsV2Response page :
                    s3Client.listObjectsV2Paginator(
                            r -> r.bucket(properties.bucket()).prefix(prefix))) {
                List<ListedObject> objects = new ArrayList<>(page.contents().size());
                page.contents()
                        .forEach(o -> objects.add(new ListedObject(o.key(), o.lastModified())));
                if (!objects.isEmpty()) {
                    pageConsumer.accept(objects);
                }
            }
        } catch (SdkException e) {
            throw new StorageException(e);
        }
    }

    /** 画像の配信 URL（DB には URL ではなく S3 キーのみ保存する）。 */
    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        return (base.endsWith("/") ? base : base + "/") + key;
    }
}
