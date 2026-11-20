package com.tripdiary.board.dto;

import com.tripdiary.board.BoardTopic;
import com.tripdiary.community.Community;
import com.tripdiary.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.function.UnaryOperator;

/** トピック詳細。 */
@Schema(description = "掲示板のトピック")
public record BoardTopicResponse(
        @Schema(example = "12") long id,
        @Schema(example = "紅葉の時期におすすめのお寺を教えてください") String title,
        @Schema(description = "本文（プレーンテキスト。改行を含む）") String body,
        UserResponse author,
        @Schema(description = "返信の件数", example = "3") long replyCount,
        @Schema(description = "最後に書き込みがあった日時（作成または最新の返信）") Instant lastActivityAt,
        Instant createdAt,
        @Schema(description = "コミュニティ内のトピックならそのコミュニティ（広場のトピックは null）", nullable = true)
                CommunityRef community) {

    /** トピックが属するコミュニティ（画面の「← コミュニティへ戻る」と、返信・削除できるかの表示に使う）。 */
    @Schema(description = "トピックが属するコミュニティ")
    public record CommunityRef(
            @Schema(example = "3") long id,
            @Schema(example = "北陸・富山のお寺巡り同好会") String name,
            @Schema(description = "オーナーのユーザー ID", example = "2") long ownerId) {

        static CommunityRef of(Community community) {
            return community == null
                    ? null
                    : new CommunityRef(
                            community.getId(), community.getName(), community.getOwner().getId());
        }
    }

    /**
     * @param photoUrl S3 キーから配信 URL を作る関数（投稿者のアイコン用）
     */
    public static BoardTopicResponse from(
            BoardTopic topic, long replyCount, UnaryOperator<String> photoUrl) {
        return new BoardTopicResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getBody(),
                UserResponse.from(topic.getUser(), photoUrl),
                replyCount,
                topic.getLastActivityAt(),
                topic.getCreatedAt(),
                CommunityRef.of(topic.getCommunity()));
    }
}
