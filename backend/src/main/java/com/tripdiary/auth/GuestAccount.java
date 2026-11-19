package com.tripdiary.auth;

import com.tripdiary.user.User;
import com.tripdiary.user.UserRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ゲストアカウント（docs/features/01_auth.md 4.8）。
 *
 * <p>採用担当者などが個人情報を登録せずにアプリを試せるよう、サンプルデータのユーザーの1人をゲストとして公開する。
 * メールアドレスとパスワードはログイン画面に表示するため、秘密情報としては扱わない。
 *
 * <p>{@code app.guest.email} と {@code app.guest.password} の両方を設定し、そのユーザーが登録済みのときだけ有効になる。
 * ゲストは複数の人が同時に使うため、ほかの人がログインできなくなる操作（パスワード変更）はできない。
 */
@Component
public class GuestAccount {

    private final UserRepository userRepository;
    private final String email;
    private final String password;

    public GuestAccount(
            UserRepository userRepository,
            @Value("${app.guest.email:}") String email,
            @Value("${app.guest.password:}") String password) {
        this.userRepository = userRepository;
        this.email = email.strip();
        this.password = password;
    }

    /** ログイン画面に表示する、ゲストのメールアドレスとパスワード。 */
    public record Credentials(String email, String password) {}

    /** ゲストログインが使えるときだけ、ゲストのメールアドレスとパスワードを返す。 */
    public Optional<Credentials> credentials() {
        if (!configured() || !userRepository.existsByEmailIgnoreCase(email)) {
            return Optional.empty();
        }
        return Optional.of(new Credentials(email, password));
    }

    /** ゲストとして公開しているユーザーかどうか。 */
    public boolean isGuest(User user) {
        return configured() && user.getEmail().equalsIgnoreCase(email);
    }

    private boolean configured() {
        return !email.isEmpty() && !password.isEmpty();
    }
}
