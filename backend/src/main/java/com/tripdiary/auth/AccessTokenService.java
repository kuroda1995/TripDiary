package com.tripdiary.auth;

import com.tripdiary.user.User;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/** アクセストークン（JWT）の発行と検証。サーバーには保存しない（ステートレス）。 */
@Service
@RequiredArgsConstructor
public class AccessTokenService {

    static final String USERNAME_CLAIM = "username";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties properties;
    private final Clock clock;

    public record IssuedAccessToken(String value, long expiresInSeconds) {}

    public IssuedAccessToken issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(properties.issuer())
                        .subject(String.valueOf(user.getId()))
                        .issuedAt(now)
                        .expiresAt(now.plus(properties.accessTokenTtl()))
                        .claim(USERNAME_CLAIM, user.getUsername())
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
    }

    /**
     * トークンを検証し、認証済みユーザーを返す。
     *
     * @throws TokenAuthenticationException 期限切れ（TOKEN_EXPIRED）、または改ざん・形式不正（INVALID_TOKEN）
     */
    public AuthenticatedUser verify(String token) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            throw TokenAuthenticationException.invalid();
        }
        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null) {
            throw TokenAuthenticationException.invalid();
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw TokenAuthenticationException.expired();
        }
        try {
            return new AuthenticatedUser(
                    Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(USERNAME_CLAIM));
        } catch (NumberFormatException e) {
            throw TokenAuthenticationException.invalid();
        }
    }
}
