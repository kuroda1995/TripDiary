package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripdiary.auth.AccessTokenService.IssuedAccessToken;
import com.tripdiary.auth.RefreshTokenService.IssuedRefreshToken;
import com.tripdiary.auth.dto.LoginRequest;
import com.tripdiary.auth.dto.SignupRequest;
import com.tripdiary.common.exception.ConflictException;
import com.tripdiary.common.exception.UnauthorizedException;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed:" + inv.getArgument(0));
        when(accessTokenService.issue(any())).thenReturn(new IssuedAccessToken("access", 900));
        when(refreshTokenService.issue(any()))
                .thenReturn(new IssuedRefreshToken("refresh", Duration.ofDays(14)));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service =
                new AuthService(
                        userRepository, passwordEncoder, accessTokenService, refreshTokenService);
    }

    private static SignupRequest signup() {
        return new SignupRequest(" Taro@Example.com ", "taro_trip", " 旅するタロウ ", "Passw0rd123");
    }

    @Test
    @DisplayName("新規登録: パスワードをハッシュ化し、メールアドレスと表示名の前後の空白を除いて保存し、トークンを発行する")
    void signupSavesUserAndIssuesTokens() {
        AuthService.AuthResult result = service.signup(signup());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("Taro@Example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("旅するタロウ");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed:Passw0rd123");
        assertThat(result.accessToken().value()).isEqualTo("access");
        assertThat(result.refreshToken().value()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("新規登録: メールアドレスが登録済みなら EMAIL_ALREADY_EXISTS で保存しない")
    void signupRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("Taro@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(signup()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(AuthService.EMAIL_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("新規登録: ユーザー名が使用済みなら USERNAME_ALREADY_EXISTS で保存しない")
    void signupRejectsDuplicateUsername() {
        when(userRepository.existsByUsernameIgnoreCase("taro_trip")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(signup()))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo(AuthService.USERNAME_ALREADY_EXISTS);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ログイン: 正しいパスワードならトークンを発行する")
    void loginSucceeds() {
        User user = TestUsers.user(1L, "taro");
        when(userRepository.findByEmailIgnoreCase("taro@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd123", "hashed")).thenReturn(true);

        AuthService.AuthResult result =
                service.login(new LoginRequest(" taro@example.com ", "Passw0rd123"));

        assertThat(result.user()).isSameAs(user);
        verify(accessTokenService).issue(user);
    }

    @Test
    @DisplayName("ログイン: パスワードが違う場合は INVALID_CREDENTIALS")
    void loginRejectsWrongPassword() {
        when(userRepository.findByEmailIgnoreCase(anyString()))
                .thenReturn(Optional.of(TestUsers.user(1L, "taro")));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertInvalidCredentials(new LoginRequest("taro@example.com", "wrong"));
        verify(accessTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("ログイン: 存在しないメールアドレスでも同じエラーにし、パスワード照合も行う（応答時間で存在を推測させない）")
    void loginWithUnknownEmailStillChecksPassword() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertInvalidCredentials(new LoginRequest("nobody@example.com", "Passw0rd123"));
        verify(passwordEncoder).matches(eq("Passw0rd123"), anyString());
    }

    @Test
    @DisplayName("トークン更新: リフレッシュトークンをローテーションしてから新しいトークンを発行する")
    void refreshRotatesAndIssues() {
        User user = TestUsers.user(1L, "taro");
        when(refreshTokenService.rotate("old")).thenReturn(user);

        AuthService.AuthResult result = service.refresh("old");

        assertThat(result.user()).isSameAs(user);
        verify(refreshTokenService).issue(user);
    }

    @Test
    @DisplayName("ログアウト: リフレッシュトークンを失効させる")
    void logoutRevokes() {
        service.logout("raw");

        verify(refreshTokenService).revoke("raw");
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .extracting("code")
                .isEqualTo(AuthService.INVALID_CREDENTIALS);
    }
}
