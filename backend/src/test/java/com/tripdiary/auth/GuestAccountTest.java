package com.tripdiary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tripdiary.auth.GuestAccount.Credentials;
import com.tripdiary.support.TestUsers;
import com.tripdiary.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestAccountTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    @DisplayName("メールアドレス・パスワードを設定し、そのユーザーが登録済みならログイン情報を返す")
    void credentialsWhenConfiguredAndUserExists() {
        when(userRepository.existsByEmailIgnoreCase("guest@example.com")).thenReturn(true);
        GuestAccount guest = new GuestAccount(userRepository, " guest@example.com ", "Guest1234");

        assertThat(guest.credentials()).contains(new Credentials("guest@example.com", "Guest1234"));
    }

    @Test
    @DisplayName("ゲストのユーザーが未登録（サンプルデータを入れていない）なら使えない")
    void noCredentialsWhenUserMissing() {
        GuestAccount guest = new GuestAccount(userRepository, "guest@example.com", "Guest1234");

        assertThat(guest.credentials()).isEmpty();
    }

    @Test
    @DisplayName("メールアドレスかパスワードが未設定なら使えず、誰もゲスト扱いにしない")
    void disabledWhenNotConfigured() {
        when(userRepository.existsByEmailIgnoreCase("")).thenReturn(true);
        GuestAccount noEmail = new GuestAccount(userRepository, "", "Guest1234");
        GuestAccount noPassword = new GuestAccount(userRepository, "taro@example.com", "");

        assertThat(noEmail.credentials()).isEmpty();
        assertThat(noPassword.credentials()).isEmpty();
        assertThat(noPassword.isGuest(TestUsers.user(1L, "taro"))).isFalse();
    }

    @Test
    @DisplayName("ゲストかどうかはメールアドレスで判定する（大文字・小文字は区別しない）")
    void isGuestByEmailIgnoringCase() {
        GuestAccount guest = new GuestAccount(userRepository, "Taro@Example.com", "Guest1234");

        assertThat(guest.isGuest(TestUsers.user(1L, "taro"))).isTrue();
        assertThat(guest.isGuest(TestUsers.user(2L, "hanako"))).isFalse();
    }
}
