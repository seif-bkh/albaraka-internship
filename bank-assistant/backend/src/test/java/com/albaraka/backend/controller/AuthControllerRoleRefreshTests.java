package com.albaraka.backend.controller;

import com.albaraka.backend.dto.TokenRefreshRequest;
import com.albaraka.backend.security.JwtUtils;
import com.albaraka.backend.security.UserDetailsImpl;
import com.albaraka.backend.security.UserDetailsServiceImpl;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.services.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerRoleRefreshTests {

    @Test
    void refreshRevokesRotatedSessionWhenStaffRoleIsInactive() {
        JwtUtils jwt = mock(JwtUtils.class);
        UserDetailsServiceImpl userDetails = mock(UserDetailsServiceImpl.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        AuthController controller = new AuthController(
                mock(AuthenticationManager.class), jwt, mock(UserRepository.class),
                mock(ClientRepository.class), mock(PasswordEncoder.class), userDetails, refreshTokens);

        TokenRefreshRequest request = new TokenRefreshRequest();
        request.setRefreshToken("signed-refresh-token");
        when(jwt.validateRefreshToken("signed-refresh-token")).thenReturn(true);
        when(jwt.getIdFromToken("signed-refresh-token")).thenReturn("current-jti");
        when(jwt.getRefreshExpirationMs()).thenReturn(60_000L);
        when(refreshTokens.rotate("current-jti", 60_000L)).thenReturn(Optional.of(
                new RefreshTokenService.Rotation("staff@example.test", "next-jti", "family")));
        when(userDetails.loadUserByUsername("staff@example.test")).thenReturn(
                new UserDetailsImpl(9L, "staff@example.test", "encoded", false,
                        AuthorityUtils.createAuthorityList("ROLE_STAFF")));

        ResponseEntity<?> response = controller.refreshtoken(request);

        assertEquals(401, response.getStatusCode().value());
        verify(refreshTokens).revokeAllForSubject("staff@example.test", RefreshTokenService.REASON_ADMIN);
    }
}
