package com.tripdiary.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tripdiary.board.BoardTopic;
import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.support.TestUsers;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** コミュニティ・カーソル・トピックの削除権限の単体テスト。 */
class CommunityTest {

    private static final Instant T0 = Instant.parse("2026-11-20T00:00:00.123456789Z");

    private static Community community(long ownerId, List<CommunityCategory> categories) {
        return new Community(TestUsers.user(ownerId, "owner"), "name", "desc", categories, T0);
    }

    @Test
    @DisplayName("カテゴリは重複を除いて宣言順に並べ、作成日時はマイクロ秒に切り捨てる")
    void categoriesAndCreatedAt() {
        Community community =
                community(
                        1L,
                        List.of(
                                CommunityCategory.PHOTOGRAPHY,
                                CommunityCategory.REGIONAL,
                                CommunityCategory.PHOTOGRAPHY));

        assertThat(community.categories())
                .containsExactly(CommunityCategory.REGIONAL, CommunityCategory.PHOTOGRAPHY);
        assertThat(community.getCreatedAt())
                .isEqualTo(Instant.parse("2026-11-20T00:00:00.123456Z"));
    }

    @Test
    @DisplayName("オーナーの判定")
    void isOwnedBy() {
        Community community = community(1L, List.of(CommunityCategory.GOSHUIN));

        assertThat(community.isOwnedBy(1L)).isTrue();
        assertThat(community.isOwnedBy(2L)).isFalse();
    }

    @Test
    @DisplayName("コミュニティ内のトピックはオーナーが管理でき、広場のトピックは誰も管理者ではない")
    void topicModeration() {
        Community community = community(1L, List.of(CommunityCategory.GOSHUIN));
        BoardTopic inCommunity = new BoardTopic(community, TestUsers.user(2L, "bob"), "t", "b", T0);
        BoardTopic onBoard = new BoardTopic(TestUsers.user(2L, "bob"), "t", "b", T0);

        assertThat(inCommunity.isModeratedBy(1L)).isTrue();
        assertThat(inCommunity.isModeratedBy(2L)).isFalse();
        assertThat(onBoard.isModeratedBy(1L)).isFalse();
    }

    @Test
    @DisplayName("カーソルはエンコードして元に戻せ、不正な形式は INVALID_CURSOR")
    void cursor() {
        CommunityCursor cursor =
                new CommunityCursor(Instant.parse("2026-11-20T00:00:00.123456Z"), 7L, 4L);

        assertThat(CommunityCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThatThrownBy(() -> CommunityCursor.decode("%%%"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> CommunityCursor.decode("YWJj")) // "abc"
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("LIKE のワイルドカードを文字として扱う")
    void escapeLike() {
        assertThat(CommunitySearchRepository.escapeLike("50%_\\")).isEqualTo("50\\%\\_\\\\");
    }
}
