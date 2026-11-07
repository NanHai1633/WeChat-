package com.hou.exam.auth;

import com.hou.exam.config.AppProperties;
import com.hou.exam.entity.SysUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String CLAIM_VERSION = "ver";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(properties.jwt().expireHours());
    }

    public String issue(SysUser user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    public Optional<TokenInfo> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenInfo(Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_VERSION, Integer.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenInfo(Long userId, Integer version) {
    }
}
