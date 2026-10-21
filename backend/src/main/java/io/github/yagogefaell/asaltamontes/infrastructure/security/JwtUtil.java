package io.github.yagogefaell.asaltamontes.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    /** Claim that tells access and refresh tokens apart, so one can never be used as the other. */
    public static final String TYPE_CLAIM = "typ";
    public static final String ACCESS_TYPE = "access";
    public static final String REFRESH_TYPE = "refresh";

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long id) {

        Map<String, Object> claims = new HashMap<>();
        claims.put(TYPE_CLAIM, ACCESS_TYPE);

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(id.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpiration()))
                .and()
                .signWith(signingKey)
                .compact();
                
    }

    public String generateRefreshToken(Long id, String jti) {
        return Jwts.builder()
                .claims()
                .add(TYPE_CLAIM, REFRESH_TYPE)
                .id(jti)
                .subject(id.toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration()))
                .and()
                .signWith(signingKey)
                .compact();
                
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // firma + estructura
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long extractId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public boolean isType(Claims claims, String type) {
        return type.equals(claims.get(TYPE_CLAIM, String.class));
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
