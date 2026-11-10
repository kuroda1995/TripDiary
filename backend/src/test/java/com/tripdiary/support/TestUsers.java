package com.tripdiary.support;

import com.tripdiary.user.User;
import org.springframework.test.util.ReflectionTestUtils;

/** 単体テスト用のユーザーを作る。 */
public final class TestUsers {

    private TestUsers() {}

    public static User user(long id, String username) {
        User user = new User(username + "@example.com", username, "hashed", "表示名");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
