package com.albaraka.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT creation and validation.
 *
 * <p>Uses the JJWT 0.12.x API. Access and refresh tokens are distinguished by a
 * {@code typ} claim so that an access token cannot be replayed against the
 * refresh endpoint to mint new credentials indefinitely.
 */
@Component
public class JwtUtils {

    private static final Logger log = LoggerFactory.getLogger(JwtUtils.class);

    /** Claim name marking the token kind. */
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expirationMs}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refreshExpirationMs}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        // The secret is configured base64-encoded; fall back to raw bytes so a
        // plain-text secret still works in local development.
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (IllegalArgumentException ex) {
            keyBytes = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String buildToken(String subject, String type, long ttlMs) {
        return buildToken(subject, type, ttlMs, null);
    }

    private String buildToken(String subject, String type, long ttlMs, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .id(jti)
                .claim(CLAIM_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        return buildToken(userPrincipal.getUsername(), TYPE_ACCESS, jwtExpirationMs);
    }

    /** Refresh tokens carry a server-registered {@code jti} (see RefreshTokenService). */
    public String generateRefreshToken(String email, String jti) {
        return buildToken(email, TYPE_REFRESH, refreshExpirationMs, jti);
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public long getJwtExpirationMs() {
        return jwtExpirationMs;
    }

    /** The {@code jti} of a (signed, unexpired) token, or null if absent. */
    public String getIdFromToken(String token) {
        return parseClaims(token).getId();
    }

    public String generateTokenFromEmail(String email, long expiration) {
        return buildToken(email, TYPE_ACCESS, expiration);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmailFromJwtToken(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Validates a token AND asserts it was issued as a refresh token.
     * Prevents an access token from being exchanged for fresh credentials.
     */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid refresh token: {}", e.getMessage());
        }
        return false;
    }
}
