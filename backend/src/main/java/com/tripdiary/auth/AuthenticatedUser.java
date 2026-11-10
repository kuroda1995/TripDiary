package com.tripdiary.auth;

/**
 * アクセストークンから復元した認証済みユーザー。コントローラーでは {@code @AuthenticationPrincipal} で受け取る。
 *
 * @param id ユーザーID
 * @param username ユーザー名
 */
public record AuthenticatedUser(long id, String username) {}
