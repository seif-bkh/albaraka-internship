package com.albaraka.backend.services;

import com.albaraka.backend.model.RefreshToken;
import com.albaraka.backend.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side refresh-token lifecycle (3.6): issue, rotate, revoke.
 *
 * <p>The JWT signature proves the token was minted by us; this table decides
 * whether it is still <em>allowed</em>. Rotation makes every refresh token
 * single-use; a replay of a consumed token is treated as theft and burns the
 * whole family, so a stolen token cannot outlive the next legitimate refresh.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public static final String REASON_ROTATED = "rotated";
    public static final String REASON_LOGOUT = "logout";
    public static final String REASON_REUSE = "reuse_detected";
    public static final String REASON_ADMIN = "admin_revoked";

    /** Result of a rotation attempt. */
    public record Rotation(String subject, String newJti, String family) {
    }

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /** Registers a brand-new token family (login). Returns the jti to embed. */
    @Transactional
    public String issue(String subject, long ttlMs) {
        return persist(subject, UUID.randomUUID().toString(), ttlMs).getJti();
    }

    /**
     * Consumes {@code jti} and issues its successor in the same family, or
     * returns empty when the token is unknown, expired, or already used
     * (in which case the family is revoked).
     */
    @Transactional
    public Optional<Rotation> rotate(String jti, long ttlMs) {
        Optional<RefreshToken> found = repo.findById(jti);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken current = found.get();
        if (current.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for subject={} family={}; revoking family",
                    current.getSubject(), current.getFamily());
            revokeFamily(current.getFamily(), REASON_REUSE);
            return Optional.empty();
        }
        if (!current.isActive()) {
            return Optional.empty();
        }
        revoke(current, REASON_ROTATED);
        RefreshToken next = persist(current.getSubject(), current.getFamily(), ttlMs);
        return Optional.of(new Rotation(current.getSubject(), next.getJti(), next.getFamily()));
    }

    /** Whether {@code jti} may still be presented (used by /logout idempotency). */
    public boolean isActive(String jti) {
        return repo.findById(jti).map(RefreshToken::isActive).orElse(false);
    }

    @Transactional
    public void revokeToken(String jti, String reason) {
        repo.findById(jti).ifPresent(t -> revoke(t, reason));
    }

    @Transactional
    public int revokeAllForSubject(String subject, String reason) {
        List<RefreshToken> active = repo.findBySubjectAndRevokedAtIsNull(subject);
        active.forEach(t -> revoke(t, reason));
        return active.size();
    }

    private void revokeFamily(String family, String reason) {
        repo.findByFamily(family).stream()
                .filter(t -> t.getRevokedAt() == null)
                .forEach(t -> revoke(t, reason));
    }

    private void revoke(RefreshToken t, String reason) {
        t.setRevokedAt(LocalDateTime.now());
        t.setRevokeReason(reason);
        repo.save(t);
    }

    private RefreshToken persist(String subject, String family, long ttlMs) {
        RefreshToken t = new RefreshToken();
        t.setJti(UUID.randomUUID().toString());
        t.setFamily(family);
        t.setSubject(subject);
        t.setExpiresAt(LocalDateTime.now().plusNanos(ttlMs * 1_000_000L));
        return repo.save(t);
    }
}
