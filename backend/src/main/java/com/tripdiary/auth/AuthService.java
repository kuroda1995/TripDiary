package com.tripdiary.auth;

import com.tripdiary.auth.AccessTokenService.IssuedAccessToken;
import com.tripdiary.auth.RefreshTokenService.IssuedRefreshToken;
import com.tripdiary.auth.dto.LoginRequest;
import com.tripdiary.auth.dto.PasswordChangeRequest;
import com.tripdiary.auth.dto.SignupRequest;
import com.tripdiary.common.exception.BadRequestException;
import com.tripdiary.common.exception.ConflictException;
import com.tripdiary.common.exception.UnauthorizedException;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 新規登録・ログイン・トークン更新・ログアウト（docs/features/01_auth.md）。 */
@Slf4j
@Service
public class AuthService {

    static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    static final String USERNAME_ALREADY_EXISTS = "USERNAME_ALREADY_EXISTS";
    static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    static final String INVALID_CURRENT_PASSWORD = "INVALID_CURRENT_PASSWORD";
    static final String PASSWORD_UNCHANGED = "PASSWORD_UNCHANGED";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    /** 存在しないメールアドレスでも照合処理を行い、応答時間からアカウントの有無を推測されないようにする。 */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** 認証結果（アクセストークン・リフレッシュトークン・ユーザー）。 */
    public record AuthResult(
            IssuedAccessToken accessToken, IssuedRefreshToken refreshToken, User user) {}

    @Transactional
    public AuthResult signup(SignupRequest request) {
        String email = request.email().trim();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(EMAIL_ALREADY_EXISTS, "このメールアドレスは登録済みです");
        }
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException(USERNAME_ALREADY_EXISTS, "このユーザー名は使用されています");
        }
        User user =
                userRepository.save(
                        new User(
                                email,
                                request.username(),
                                passwordEncoder.encode(request.password()),
                                request.displayName().trim()));
        log.info("User signed up userId={}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        Optional<User> found = userRepository.findByEmailIgnoreCase(request.email().trim());
        String hash = found.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (found.isEmpty() || !matches) {
            log.info("Login failed");
            throw new UnauthorizedException(INVALID_CREDENTIALS, "メールアドレスまたはパスワードが正しくありません");
        }
        User user = found.get();
        log.info("User logged in userId={}", user.getId());
        return issueTokens(user);
    }

    /** リフレッシュトークンをローテーションし、新しいトークンを発行する。 */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawRefreshToken) {
        User user = refreshTokenService.rotate(rawRefreshToken);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /**
     * パスワードを変更する（docs/features/01_auth.md 5章）。
     *
     * <p>他の端末のログインを切るため全リフレッシュトークンを失効させ、操作中の端末には新しいトークンを発行する。 （他の端末のアクセストークンは JWT
     * のため、有効期限の15分までは使える。）
     */
    @Transactional
    public AuthResult changePassword(long userId, PasswordChangeRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new UnauthorizedException("UNAUTHORIZED", "ログインが必要です"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.info("Password change rejected: wrong current password userId={}", userId);
            throw new BadRequestException(INVALID_CURRENT_PASSWORD, "現在のパスワードが正しくありません");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException(PASSWORD_UNCHANGED, "新しいパスワードが現在のパスワードと同じです");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId);
        log.info("Password changed userId={}", userId);
        return issueTokens(user);
    }

    private AuthResult issueTokens(User user) {
        return new AuthResult(
                accessTokenService.issue(user), refreshTokenService.issue(user), user);
    }
}
