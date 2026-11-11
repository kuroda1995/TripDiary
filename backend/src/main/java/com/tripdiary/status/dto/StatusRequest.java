package com.tripdiary.status.dto;

import com.tripdiary.spot.SpotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** ステータスの設定・変更。 */
@Schema(description = "ステータスの設定")
public record StatusRequest(
        @Schema(description = "VISITED（訪問済み）/ WANT_TO_GO（行きたい）")
                @NotNull(message = "ステータスを指定してください")
                SpotStatus status) {}
