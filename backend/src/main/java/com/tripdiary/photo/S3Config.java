package com.tripdiary.photo;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 クライアントの設定。
 *
 * <p>本番は ECS タスクロールの認証情報（DefaultCredentialsProvider）で AWS の S3 に接続する。 ローカル・テストでは {@code
 * app.storage.endpoint} に S3Mock を指定し、パス形式の URL と固定の認証情報で接続する。
 */
@Configuration(proxyBeanMethods = false)
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        var builder =
                S3Client.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(credentials(properties));
        if (properties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties properties) {
        var builder =
                S3Presigner.builder()
                        .region(Region.of(properties.region()))
                        .credentialsProvider(credentials(properties));
        if (properties.hasCustomEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint()))
                    .serviceConfiguration(
                            S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        if (properties.hasCustomEndpoint()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
