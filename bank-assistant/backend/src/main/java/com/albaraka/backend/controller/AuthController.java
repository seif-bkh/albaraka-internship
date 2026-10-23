package com.albaraka.backend.controller;

import com.albaraka.backend.dto.CurrentUserResponse;
import com.albaraka.backend.dto.JwtResponse;
import com.albaraka.backend.dto.LoginRequest;
import com.albaraka.backend.dto.TokenRefreshRequest;
import com.albaraka.backend.security.JwtUtils;
import com.albaraka.backend.security.UserDetailsImpl;
import com.albaraka.backend.security.UserDetailsServiceImpl;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.albaraka.backend.dto.SignupRequest;
import com.albaraka.backend.model.Role;
import com.albaraka.backend.model.User;
import com.albaraka.backend.model.Client;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder encoder;
    private final UserDetailsServiceImpl userDetailsService;
    private final com.albaraka.backend.services.RefreshTokenService refreshTokens;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtils jwtUtils,
                          UserRepository userRepository,
                          ClientRepository clientRepository,
                          PasswordEncoder encoder,
                          UserDetailsServiceImpl userDetailsService,
                          com.albaraka.backend.services.RefreshTokenService refreshTokens) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.encoder = encoder;
        this.userDetailsService = userDetailsService;
        this.refreshTokens = refreshTokens;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public CurrentUserResponse currentUser(Authentication authentication) {
        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toList());
        return new CurrentUserResponse(principal.getId(), principal.getUsername(), roles);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getMotDePasse()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(401).body("Error: Unauthorized - " + e.getMessage());
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        String jwt = jwtUtils.generateJwtToken(authentication);
        String jti = refreshTokens.issue(userDetails.getUsername(), jwtUtils.getRefreshExpirationMs());
        String refreshToken = jwtUtils.generateRefreshToken(userDetails.getUsername(), jti);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt, refreshToken, userDetails.getId(),
                userDetails.getUsername(), roles));
    }

    /**
     * Rotating refresh: the presented token is consumed server-side and a new
     * refresh token is returned alongside the access token. A consumed/revoked
     * token yields 401 (and, on reuse, revokes the whole family — see
     * {@link com.albaraka.backend.services.RefreshTokenService}).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshtoken(@Valid @RequestBody TokenRefreshRequest request) {
        String presented = request.getRefreshToken();
        if (!jwtUtils.validateRefreshToken(presented)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid refresh token"));
        }
        String jti = jwtUtils.getIdFromToken(presented);
        if (jti == null) {
            // Pre-V4 token without a server id: force a fresh login once.
            return ResponseEntity.status(401).body(Map.of("error", "Session expirée, veuillez vous reconnecter."));
        }
        var rotated = refreshTokens.rotate(jti, jwtUtils.getRefreshExpirationMs());
        if (rotated.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Session révoquée, veuillez vous reconnecter."));
        }
        String email = rotated.get().subject();
        try {
            // Re-read the user so the response carries id + roles again; otherwise
            // the client would lose its authorization after every refresh.
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (userDetails instanceof UserDetailsImpl principal
                    && principal.isStaffPrincipal() && !userDetails.isEnabled()) {
                refreshTokens.revokeAllForSubject(email,
                        com.albaraka.backend.services.RefreshTokenService.REASON_ADMIN);
                return ResponseEntity.status(401).body(Map.of(
                        "error", "Compte ou rôle désactivé. Veuillez contacter un administrateur."));
            }
            Long id = (userDetails instanceof UserDetailsImpl ud) ? ud.getId() : null;
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(item -> item.getAuthority())
                    .collect(Collectors.toList());
            String token = jwtUtils.generateTokenFromEmail(email, jwtUtils.getJwtExpirationMs());
            String newRefresh = jwtUtils.generateRefreshToken(email, rotated.get().newJti());
            return ResponseEntity.ok(new JwtResponse(token, newRefresh, id, email, roles));
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException e) {
            refreshTokens.revokeAllForSubject(email, com.albaraka.backend.services.RefreshTokenService.REASON_ADMIN);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Erreur: Le compte associé à ce jeton n'existe plus. Veuillez vous reconnecter."));
        }
    }

    /** Revokes the presented refresh token. Idempotent; never leaks token validity. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) TokenRefreshRequest request) {
        if (request != null && request.getRefreshToken() != null
                && jwtUtils.validateRefreshToken(request.getRefreshToken())) {
            String jti = jwtUtils.getIdFromToken(request.getRefreshToken());
            if (jti != null) {
                refreshTokens.revokeToken(jti, com.albaraka.backend.services.RefreshTokenService.REASON_LOGOUT);
            }
        }
        return ResponseEntity.ok(Map.of("message", "Déconnecté."));
    }

    @PostMapping("/register-client")
    public ResponseEntity<?> registerClient(@Valid @RequestBody SignupRequest signUpRequest) {
        if (clientRepository.findByEmail(signUpRequest.getEmail()).isPresent() || userRepository.findByEmail(signUpRequest.getEmail()).isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of("error", "Erreur: L'email est déjà utilisé!"));
        }

        Client client = new Client();
        client.setEmail(signUpRequest.getEmail());
        client.setMotDePasse(encoder.encode(signUpRequest.getMotDePasse()));
        client.setNom(signUpRequest.getNom());
        client.setPrenom(signUpRequest.getPrenom());
        client.setTelephone(signUpRequest.getTelephone());
        client.setLanguePreferee("fr");
        client.setSegment("PARTICULIER");
        client.setRole(Role.UTILISATEUR);

        clientRepository.save(client);

        return ResponseEntity.ok(Map.of("message", "Utilisateur enregistré avec succès!"));
    }
}
