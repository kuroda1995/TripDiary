package com.tripdiary.user;

import com.tripdiary.common.exception.NotFoundException;
import com.tripdiary.photo.PhotoStorage;
import com.tripdiary.user.dto.ProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 他のユーザーからも見えるプロフィール（docs/features/06_follow.md 4.1）。 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final JdbcUserStatsReader statsReader;
    private final PhotoStorage photoStorage;

    /**
     * @param viewerId 閲覧者（未ログインなら null）
     */
    @Transactional(readOnly = true)
    public ProfileResponse profile(String username, Long viewerId) {
        User user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                UserService.USER_NOT_FOUND, "ユーザーが存在しません"));
        return ProfileResponse.from(
                user, statsReader.read(user.getId(), viewerId), photoStorage::publicUrl);
    }
}
