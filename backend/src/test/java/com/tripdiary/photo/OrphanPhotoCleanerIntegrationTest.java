package com.tripdiary.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tripdiary.IntegrationTest;
import com.tripdiary.support.ApiTestClient;
import com.tripdiary.support.ApiTestClient.TestAccount;
import com.tripdiary.support.MutableClock;
import com.tripdiary.support.PhotoTestClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 孤立ファイル削除（PHOTO-06）の結合テスト。S3Mock 上の実ファイルと DB の参照を突き合わせる。 */
@IntegrationTest
class OrphanPhotoCleanerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NamedParameterJdbcTemplate namedJdbc;
    @Autowired PhotoStorage photoStorage;
    @Autowired StorageProperties properties;

    private TestAccount alice;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE users RESTART IDENTITY CASCADE");
        alice = ApiTestClient.signup(mockMvc, "alice");
    }

    /** 「今」を指定した時刻にしたクリーナー（S3 の更新日時は書き換えられないため、時計の側を進める）。 */
    private OrphanPhotoCleaner cleanerAt(Instant now) {
        return new OrphanPhotoCleaner(photoStorage, namedJdbc, properties, new MutableClock(now));
    }

    @Test
    @DisplayName("24時間を過ぎた未使用ファイルだけを削除し、投稿・アイコンで使用中のファイルと新しいファイルは残す")
    void deletesOnlyOldUnusedFiles() throws Exception {
        String attached = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String orphan = PhotoTestClient.uploadSpotPhoto(mockMvc, alice);
        String avatarInUse =
                PhotoTestClient.upload(
                        mockMvc, alice, "AVATAR", "image/png", PhotoTestClient.JPEG_BYTES);
        String avatarOrphan =
                PhotoTestClient.upload(
                        mockMvc, alice, "AVATAR", "image/png", PhotoTestClient.JPEG_BYTES);
        long kyoto =
                jdbc.queryForObject("SELECT id FROM area_tags WHERE slug = 'kyoto'", Long.class);
        mockMvc.perform(
                        alice.authorize(post("/api/v1/spots"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title":"t","locationName":"l","areaTagIds":[%d],"visibility":"PUBLIC",
                                         "photos":[{"s3Key":"%s","sortOrder":0}]}
                                        """
                                                .formatted(kyoto, attached)))
                .andExpect(status().isCreated());
        // アイコンの保存（AUTH-07）は未実装のため、DB を直接更新して「使用中」にする
        jdbc.update("UPDATE users SET avatar_s3_key = ? WHERE id = ?", avatarInUse, alice.id());

        // まだ 24 時間たっていないので消さない
        cleanerAt(Instant.now().plus(Duration.ofHours(23))).cleanUp();
        assertThat(photoStorage.head(orphan)).isPresent();
        assertThat(photoStorage.head(avatarOrphan)).isPresent();

        // バケットは他のテストクラスと共有のため、件数ではなくファイルごとに確認する
        int deleted = cleanerAt(Instant.now().plus(Duration.ofHours(25))).cleanUp();

        assertThat(deleted).isGreaterThanOrEqualTo(2);
        assertThat(photoStorage.head(orphan)).isEmpty();
        assertThat(photoStorage.head(avatarOrphan)).isEmpty();
        assertThat(photoStorage.head(attached)).isPresent();
        assertThat(photoStorage.head(avatarInUse)).isPresent();
    }
}
