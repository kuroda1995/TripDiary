package com.tripdiary.spot.dto;

import com.tripdiary.spot.SpotPhoto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.function.UnaryOperator;

/** スポットの写真（s3Key は全スポットで一意のため、写真の識別にも使う）。 */
@Schema(description = "スポットの写真")
public record SpotPhotoResponse(
        @Schema(description = "編集時に photos[].s3Key として送り返すキー", example = "spots/42/9c1e....jpg")
                String s3Key,
        @Schema(description = "表示用の URL", example = "https://img.example.com/spots/42/9c1e....jpg")
                String url,
        @Schema(description = "表示順（0 がカバー写真）", example = "0") int sortOrder) {

    static SpotPhotoResponse from(SpotPhoto photo, UnaryOperator<String> photoUrl) {
        return new SpotPhotoResponse(
                photo.getS3Key(), photoUrl.apply(photo.getS3Key()), photo.getSortOrder());
    }
}
