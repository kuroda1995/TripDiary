package com.tripdiary.community;

import com.tripdiary.common.exception.ConflictException;
import com.tripdiary.common.exception.ForbiddenException;
import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.community.MembershipRepository.MemberRow;
import com.tripdiary.community.dto.CommunityPageResponse;
import com.tripdiary.community.dto.CommunityRequest;
import com.tripdiary.community.dto.CommunityResponse;
import com.tripdiary.community.dto.CommunitySummaryResponse;
import com.tripdiary.community.dto.MemberPageResponse;
import com.tripdiary.community.dto.MemberResponse;
import com.tripdiary.community.dto.MembershipResponse;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.spot.SpotCursor;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import com.tripdiary.user.dto.UserResponse;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ユーザーが作るテーマ別コミュニティ（docs/features/12_communities.md）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    public static final String COMMUNITY_NOT_FOUND = "COMMUNITY_NOT_FOUND";
    public static final String COMMUNITY_NAME_TAKEN = "COMMUNITY_NAME_TAKEN";
    public static final String OWNER_CANNOT_LEAVE = "OWNER_CANNOT_LEAVE";
    public static final String COMMUNITY_MEMBERSHIP_REQUIRED = "COMMUNITY_MEMBERSHIP_REQUIRED";

    private final CommunityRepository communityRepository;
    private final CommunitySearchRepository searchRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PhotoStorage photoStorage;
    private final Clock clock;

    /**
     * コミュニティを探す（キーワード・カテゴリで絞り込み、新着順か人気順）。
     *
     * @param keyword 名前・説明文の部分一致（空白だけなら絞り込まない）
     * @param viewerId 閲覧者（未ログインは null。「参加中」の表示に使う）
     */
    @Transactional(readOnly = true)
    public CommunityPageResponse search(
            String keyword,
            CommunityCategory category,
            CommunitySort sort,
            Long viewerId,
            String cursor,
            int limit) {
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        CommunityCursor after = cursor == null ? null : CommunityCursor.decode(cursor);
        List<CommunityCursor> hits =
                searchRepository.search(
                        new CommunitySearchRepository.Criteria(normalizedKeyword, category, sort),
                        after,
                        limit + 1);
        boolean hasNext = hits.size() > limit;
        List<CommunityCursor> page = hasNext ? hits.subList(0, limit) : hits;
        String nextCursor = hasNext ? page.get(page.size() - 1).encode() : null;

        List<Long> ids = page.stream().map(CommunityCursor::id).toList();
        Map<Long, Community> byId =
                communityRepository.findWithOwnerByIdIn(ids).stream()
                        .collect(Collectors.toMap(Community::getId, Function.identity()));
        Set<Long> joined =
                viewerId == null
                        ? Set.of()
                        : membershipRepository.joinedCommunityIds(viewerId, ids);
        // 検索の並び順のまま返す（検索とエンティティの読み込みの間に削除されたものは除く）
        List<CommunitySummaryResponse> items =
                ids.stream()
                        .filter(byId::containsKey)
                        .map(
                                id ->
                                        CommunitySummaryResponse.from(
                                                byId.get(id),
                                                joined.contains(id),
                                                photoStorage::publicUrl))
                        .toList();
        return new CommunityPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public CommunityResponse get(long communityId, Long viewerId) {
        Community community = findCommunity(communityId);
        return toResponse(community, viewerId);
    }

    /** 作成する。作成者はオーナーとして自動で参加する。同じ名前があれば 409 COMMUNITY_NAME_TAKEN。 */
    @Transactional
    public CommunityResponse create(long userId, CommunityRequest request) {
        String name = request.name().strip();
        if (communityRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException(COMMUNITY_NAME_TAKEN, "同じ名前のコミュニティがすでにあります");
        }
        User owner = userRepository.getReferenceById(userId);
        Community community =
                communityRepository.saveAndFlush(
                        new Community(
                                owner,
                                name,
                                request.description().strip(),
                                request.categories(),
                                clock.instant()));
        membershipRepository.insertIfAbsent(community.getId(), userId, community.getCreatedAt());
        log.info("Community created communityId={}", community.getId());
        // member_count はトリガーで更新されるため、エンティティの値ではなく DB から読む
        return CommunityResponse.from(
                community,
                membershipRepository.memberCount(community.getId()),
                0,
                true,
                photoStorage::publicUrl);
    }

    /** 削除はオーナーのみ（他人は 403）。メンバー・トピック・返信もまとめて削除される（ON DELETE CASCADE）。 */
    @Transactional
    public void delete(long communityId, long userId) {
        Community community = findCommunity(communityId);
        if (!community.isOwnedBy(userId)) {
            throw new ForbiddenException("他のユーザーのコミュニティは削除できません");
        }
        communityRepository.delete(community);
        log.info("Community deleted communityId={}", communityId);
    }

    /** 参加する（冪等）。 */
    @Transactional
    public MembershipResponse join(long communityId, long userId) {
        findCommunity(communityId);
        membershipRepository.insertIfAbsent(communityId, userId, clock.instant());
        log.info("Community joined communityId={}", communityId);
        return new MembershipResponse(
                communityId, true, membershipRepository.memberCount(communityId));
    }

    /** 退出する（冪等）。オーナーは退出できない（409 OWNER_CANNOT_LEAVE。やめるときはコミュニティを削除する）。 */
    @Transactional
    public MembershipResponse leave(long communityId, long userId) {
        Community community = findCommunity(communityId);
        if (community.isOwnedBy(userId)) {
            throw new ConflictException(OWNER_CANNOT_LEAVE, "作成者は退出できません。コミュニティを削除してください");
        }
        membershipRepository.delete(communityId, userId);
        log.info("Community left communityId={}", communityId);
        return new MembershipResponse(
                communityId, false, membershipRepository.memberCount(communityId));
    }

    /** メンバー一覧（参加した順。オーナーは最初に参加しているので先頭になる）。 */
    @Transactional(readOnly = true)
    public MemberPageResponse listMembers(long communityId, String cursor, int limit) {
        Community community = findCommunity(communityId);
        MemberRow after = null;
        if (cursor != null) {
            SpotCursor decoded = SpotCursor.decode(cursor);
            after = new MemberRow(decoded.id(), decoded.createdAt());
        }
        List<MemberRow> rows = membershipRepository.findMembers(communityId, after, limit + 1);
        boolean hasNext = rows.size() > limit;
        List<MemberRow> page = hasNext ? rows.subList(0, limit) : rows;
        String nextCursor = null;
        if (hasNext) {
            MemberRow last = page.get(page.size() - 1);
            nextCursor = new SpotCursor(last.joinedAt(), last.userId()).encode();
        }
        Map<Long, User> users =
                userRepository.findAllById(page.stream().map(MemberRow::userId).toList()).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MemberResponse> items =
                page.stream()
                        .filter(row -> users.containsKey(row.userId()))
                        .map(
                                row ->
                                        new MemberResponse(
                                                UserResponse.from(
                                                        users.get(row.userId()),
                                                        photoStorage::publicUrl),
                                                community.isOwnedBy(row.userId()),
                                                row.joinedAt()))
                        .toList();
        return new MemberPageResponse(items, nextCursor);
    }

    /**
     * コミュニティを読む（オーナーも読み込む）。掲示板（BoardService）からも使う。
     *
     * @throws NotFoundException 存在しない場合（COMMUNITY_NOT_FOUND）
     */
    @Transactional(readOnly = true)
    public Community findCommunity(long communityId) {
        return communityRepository
                .findWithOwnerById(communityId)
                .orElseThrow(
                        () -> new NotFoundException(COMMUNITY_NOT_FOUND, "指定されたコミュニティは存在しません"));
    }

    /**
     * コミュニティ内で書き込めるか確かめる（メンバーのみ）。
     *
     * @throws ForbiddenException メンバーでない場合（COMMUNITY_MEMBERSHIP_REQUIRED）
     */
    @Transactional(readOnly = true)
    public void requireMember(long communityId, long userId) {
        if (!membershipRepository.isMember(communityId, userId)) {
            throw new ForbiddenException(COMMUNITY_MEMBERSHIP_REQUIRED, "コミュニティに参加すると書き込めます");
        }
    }

    private CommunityResponse toResponse(Community community, Long viewerId) {
        long id = community.getId();
        boolean joined = viewerId != null && membershipRepository.isMember(id, viewerId);
        return CommunityResponse.from(
                community,
                community.getMemberCount(),
                membershipRepository.topicCount(id),
                joined,
                photoStorage::publicUrl);
    }
}
