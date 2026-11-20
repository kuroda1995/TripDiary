package com.tripdiary.helpful;

import com.tripdiary.helpful.dto.HelpfulResponse;
import com.tripdiary.spot.SpotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 参考になった（docs/features/11_community_reactions.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelpfulService {

    private final HelpfulRepository helpfulRepository;
    private final SpotService spotService;

    /** 付ける（冪等）。他人の非公開スポット・存在しないスポットは SPOT_NOT_FOUND。 */
    @Transactional
    public HelpfulResponse mark(long spotId, long userId) {
        spotService.getVisible(spotId, userId);
        helpfulRepository.insertIfAbsent(userId, spotId);
        log.info("Spot marked helpful spotId={}", spotId);
        return new HelpfulResponse(spotId, true, helpfulRepository.count(spotId));
    }

    /** 外す（冪等）。 */
    @Transactional
    public HelpfulResponse unmark(long spotId, long userId) {
        spotService.getVisible(spotId, userId);
        helpfulRepository.delete(userId, spotId);
        log.info("Spot unmarked helpful spotId={}", spotId);
        return new HelpfulResponse(spotId, false, helpfulRepository.count(spotId));
    }
}
