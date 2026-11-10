package com.tripdiary.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class PhotoStorageTest {

    private final StorageProperties properties =
            new StorageProperties(
                    "tripdiary-photos",
                    "ap-northeast-1",
                    null,
                    null,
                    null,
                    "https://img.example.com",
                    Duration.ofMinutes(5),
                    Duration.ofHours(24));

    private final S3Client s3Client = mock(S3Client.class);

    // 署名はローカルで計算できるため、本物の Presigner を使って URL の中身を確かめる
    private final S3Presigner presigner =
            S3Presigner.builder()
                    .region(Region.AP_NORTHEAST_1)
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("AKIDEXAMPLE", "secret")))
                    .build();

    private final PhotoStorage storage = new PhotoStorage(s3Client, presigner, properties);

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @Test
    @DisplayName("署名付き URL: 有効期限5分で、Content-Type とサイズを署名の対象に含める")
    void presignPutSignsContentTypeAndLength() {
        var upload = storage.presignPut("spots/1/a.jpg", "image/jpeg", 2457600);

        URI url = URI.create(upload.url());
        assertThat(url.getHost()).isEqualTo("tripdiary-photos.s3.ap-northeast-1.amazonaws.com");
        assertThat(url.getPath()).isEqualTo("/spots/1/a.jpg");
        assertThat(url.getQuery())
                .contains("X-Amz-Expires=300")
                .containsPattern("X-Amz-SignedHeaders=[^&]*content-length")
                .containsPattern("X-Amz-SignedHeaders=[^&]*content-type");
        assertThat(upload.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("配信 URL は配信ドメインと S3 キーから組み立てる")
    void publicUrl() {
        assertThat(storage.publicUrl("spots/1/a.jpg"))
                .isEqualTo("https://img.example.com/spots/1/a.jpg");
    }

    @Test
    @DisplayName("head: 存在しないファイルは空（NoSuchKey / 404）、それ以外の失敗は STORAGE_ERROR")
    @SuppressWarnings("unchecked")
    void headHandlesMissingFiles() {
        when(s3Client.headObject(any(Consumer.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(404).build())
                .thenThrow(S3Exception.builder().statusCode(403).build())
                .thenThrow(SdkClientException.create("connection refused"));

        assertThat(storage.head("a")).isEmpty();
        assertThat(storage.head("a")).isEmpty();
        assertThatThrownBy(() -> storage.head("a")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> storage.head("a"))
                .isInstanceOf(StorageException.class)
                .extracting("code")
                .isEqualTo(StorageException.CODE);
        verify(s3Client, times(4)).headObject(any(Consumer.class));
    }

    @Test
    @DisplayName("deleteAll: 1000件ずつに分けて削除し、失敗した件数を返す")
    @SuppressWarnings("unchecked")
    void deleteAllInBatches() {
        when(s3Client.deleteObjects(any(Consumer.class)))
                .thenReturn(
                        DeleteObjectsResponse.builder()
                                .errors(S3Error.builder().key("k0").build())
                                .build())
                .thenReturn(DeleteObjectsResponse.builder().build());
        List<String> keys = IntStream.range(0, 1500).mapToObj(i -> "k" + i).toList();

        int failed = storage.deleteAll(keys);

        assertThat(failed).isEqualTo(1);
        ArgumentCaptor<Consumer<DeleteObjectsRequest.Builder>> captor =
                ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client, times(2)).deleteObjects(captor.capture());
        List<Integer> sizes =
                captor.getAllValues().stream()
                        .map(
                                c -> {
                                    var builder = DeleteObjectsRequest.builder();
                                    c.accept(builder);
                                    DeleteObjectsRequest request = builder.build();
                                    assertThat(request.bucket()).isEqualTo("tripdiary-photos");
                                    return request.delete().objects().size();
                                })
                        .toList();
        assertThat(sizes).containsExactly(1000, 500);
    }

    @Test
    @DisplayName("deleteAll: 接続できなければ STORAGE_ERROR")
    @SuppressWarnings("unchecked")
    void deleteAllFailure() {
        when(s3Client.deleteObjects(any(Consumer.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> storage.deleteAll(List.of("a")))
                .isInstanceOf(StorageException.class);
    }
}
