package com.albaraka.backend.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Rotation, reuse detection and revocation of refresh tokens (3.6). */
@SpringBootTest
@Transactional
class RefreshTokenServiceTests {

    @Autowired RefreshTokenService service;

    @Test
    void rotationConsumesTheTokenAndReuseBurnsTheFamily() {
        String first = service.issue("a@b.tn", 60_000);
        assertThat(service.isActive(first)).isTrue();

        Optional<RefreshTokenService.Rotation> r1 = service.rotate(first, 60_000);
        assertThat(r1).isPresent();
        assertThat(service.isActive(first)).isFalse();
        assertThat(service.isActive(r1.get().newJti())).isTrue();

        // Replay of the consumed token: rejected AND the successor is revoked too.
        assertThat(service.rotate(first, 60_000)).isEmpty();
        assertThat(service.isActive(r1.get().newJti())).isFalse();
        assertThat(service.rotate(r1.get().newJti(), 60_000)).isEmpty();
    }

    @Test
    void expiredUnknownAndLoggedOutTokensAreRejected() {
        assertThat(service.rotate("does-not-exist", 60_000)).isEmpty();
        String expired = service.issue("a@b.tn", -1);
        assertThat(service.rotate(expired, 60_000)).isEmpty();

        String jti = service.issue("a@b.tn", 60_000);
        service.revokeToken(jti, RefreshTokenService.REASON_LOGOUT);
        assertThat(service.isActive(jti)).isFalse();
    }

    @Test
    void adminRevocationSignsAUserOutEverywhere() {
        String t1 = service.issue("x@y.tn", 60_000);
        String t2 = service.issue("x@y.tn", 60_000);
        String other = service.issue("z@y.tn", 60_000);
        assertThat(service.revokeAllForSubject("x@y.tn", RefreshTokenService.REASON_ADMIN)).isEqualTo(2);
        assertThat(service.isActive(t1)).isFalse();
        assertThat(service.isActive(t2)).isFalse();
        assertThat(service.isActive(other)).isTrue();
    }
}
