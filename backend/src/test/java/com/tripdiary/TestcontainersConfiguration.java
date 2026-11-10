package com.tripdiary;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 結合テスト用のコンテナ。
 *
 * <ul>
 *   <li>PostgreSQL 16（本番と同じ。docs/rules.md 6章: H2 等の代替 DB を使わない）
 *   <li>S3Mock（S3 互換。テストから実 AWS に接続しない）
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    public static final String S3MOCK_IMAGE = "adobe/s3mock:5.2.3";
    public static final String BUCKET = "tripdiary-photos";
    static final int S3MOCK_PORT = 9090;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }

    @Bean
    GenericContainer<?> s3MockContainer() {
        return new GenericContainer<>(DockerImageName.parse(S3MOCK_IMAGE))
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
                .withExposedPorts(S3MOCK_PORT)
                .waitingFor(Wait.forHttp("/").forPort(S3MOCK_PORT));
    }

    @Bean
    DynamicPropertyRegistrar s3MockProperties(GenericContainer<?> s3MockContainer) {
        return registry -> {
            String endpoint =
                    "http://%s:%d"
                            .formatted(
                                    s3MockContainer.getHost(),
                                    s3MockContainer.getMappedPort(S3MOCK_PORT));
            registry.add("app.storage.endpoint", () -> endpoint);
            // prod プロファイルのテストでも S3Mock に接続できるよう、認証情報も上書きする
            registry.add("app.storage.access-key", () -> "test");
            registry.add("app.storage.secret-key", () -> "test");
            registry.add("app.storage.bucket", () -> BUCKET);
            registry.add("app.storage.public-base-url", () -> endpoint + "/" + BUCKET);
            // テスト中に定期削除が走らないようにする（テストでは OrphanPhotoCleaner を直接呼ぶ）
            registry.add("app.storage.orphan-cleanup.enabled", () -> "false");
        };
    }
}
