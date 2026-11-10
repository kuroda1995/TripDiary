package com.tripdiary.auth;

import com.tripdiary.common.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code Authorization: Bearer <JWT>} を検証し、認証済みユーザーを SecurityContext と MDC（userId）に設定する。
 *
 * <p>ヘッダが無いリクエストは未認証のまま通す（認可の判断は SecurityConfig）。 トークンが不正・期限切れの場合は即座に 401 を返す。 Servlet
 * フィルタとして二重登録されないよう Bean にはせず、SecurityConfig で生成する。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<SimpleGrantedAuthority> USER_AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AccessTokenService accessTokenService;
    private final AuthenticationEntryPoint entryPoint;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    public JwtAuthenticationFilter(
            AccessTokenService accessTokenService, AuthenticationEntryPoint entryPoint) {
        this.accessTokenService = accessTokenService;
        this.entryPoint = entryPoint;
    }

    /** 認証 API（ログイン・トークン更新など）では期限切れのトークンが送られても妨げないよう検証しない。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(RefreshTokenCookies.PATH + "/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            AuthenticatedUser user =
                    accessTokenService.verify(header.substring(BEARER_PREFIX.length()).trim());
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            user, null, USER_AUTHORITIES));
            contextHolder.setContext(context);
            // MDC は RequestIdFilter がリクエスト終了時にクリアする
            MDC.put(MdcKeys.USER_ID, String.valueOf(user.id()));
        } catch (TokenAuthenticationException e) {
            contextHolder.clearContext();
            entryPoint.commence(request, response, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
