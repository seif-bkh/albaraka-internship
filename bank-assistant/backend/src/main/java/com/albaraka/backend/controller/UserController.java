package com.albaraka.backend.controller;

import com.albaraka.backend.dto.StaffUserDtos.CreateUserRequest;
import com.albaraka.backend.dto.StaffUserDtos.UpdateUserRequest;
import com.albaraka.backend.dto.StaffUserDtos.UserResponse;
import com.albaraka.backend.model.User;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.services.AdminAccessAuditService;
import com.albaraka.backend.services.AdminRoleService;
import com.albaraka.backend.services.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class UserController {
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokens;
    private final AdminRoleService roles;
    private final AdminAccessAuditService audit;

    public UserController(UserRepository userRepository,
                          ClientRepository clientRepository,
                          PasswordEncoder passwordEncoder,
                          RefreshTokenService refreshTokens,
                          AdminRoleService roles,
                          AdminAccessAuditService audit) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokens = refreshTokens;
        this.roles = roles;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findInternalUsers().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@Valid @RequestBody CreateUserRequest request,
                                        Authentication authentication) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent() || clientRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cette adresse e-mail est déjà utilisée."));
        }
        var role = roles.requireAssignableStaffRole(request.role());

        User user = new User();
        user.setEmail(email);
        user.setMotDePasse(passwordEncoder.encode(request.motDePasse()));
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setRole(role.getCode());
        user.setActif(true);
        User saved = userRepository.save(user);
        audit.record("STAFF_CREATED", authentication.getName(), role.getCode(), saved.getId(),
                "Compte personnel créé: " + email);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserRequest request,
                                                  Authentication authentication) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        var role = roles.requireAssignableStaffRole(request.role());
        boolean roleChanged = !role.getCode().equals(user.getRole());
        boolean activeChanged = user.isActif() != request.actif();
        String password = request.motDePasse();
        boolean passwordChange = password != null && !password.isBlank();
        if (passwordChange && password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit contenir au moins 8 caractères.");
        }

        if (user.isActif() && SUPER_ADMIN.equals(user.getRole())
                && (roleChanged || !request.actif())) {
            roles.lockSuperAdminMembership();
        }
        if (user.isActif() && SUPER_ADMIN.equals(user.getRole())
                && (roleChanged || !request.actif())
                && userRepository.countByRoleAndActifTrue(SUPER_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de rétrograder ou désactiver le dernier Super Administrateur actif.");
        }

        String previousRole = user.getRole();
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setRole(role.getCode());
        user.setActif(request.actif());
        if (passwordChange) user.setMotDePasse(passwordEncoder.encode(password));
        User saved = userRepository.save(user);

        if (roleChanged || activeChanged || passwordChange) {
            refreshTokens.revokeAllForSubject(saved.getEmail(), RefreshTokenService.REASON_ADMIN);
        }
        if (roleChanged) {
            audit.record("STAFF_ROLE_ASSIGNED", authentication.getName(), role.getCode(), saved.getId(),
                    "role=" + previousRole + " -> " + role.getCode());
        }
        if (activeChanged) {
            audit.record(request.actif() ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED",
                    authentication.getName(), role.getCode(), saved.getId(),
                    "Compte " + (request.actif() ? "activé" : "désactivé"));
        }
        if (passwordChange) {
            audit.record("STAFF_PASSWORD_RESET", authentication.getName(), role.getCode(), saved.getId(),
                    "Mot de passe réinitialisé par un Super Administrateur");
        }
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        if (user.isActif() && SUPER_ADMIN.equals(user.getRole())) {
            roles.lockSuperAdminMembership();
        }
        if (user.isActif() && SUPER_ADMIN.equals(user.getRole())
                && userRepository.countByRoleAndActifTrue(SUPER_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible de supprimer le dernier Super Administrateur actif.");
        }
        refreshTokens.revokeAllForSubject(user.getEmail(), RefreshTokenService.REASON_ADMIN);
        audit.record("STAFF_DELETED", authentication.getName(), user.getRole(), user.getId(),
                "Compte supprimé: " + user.getEmail());
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    /** Explicit "sign this user out everywhere" (incident response). */
    @PostMapping("/{id}/revoke-sessions")
    @Transactional
    public ResponseEntity<?> revokeSessions(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compte introuvable."));
        int revoked = refreshTokens.revokeAllForSubject(user.getEmail(), RefreshTokenService.REASON_ADMIN);
        audit.record("STAFF_SESSIONS_REVOKED", authentication.getName(), user.getRole(), user.getId(),
                "Sessions révoquées: " + revoked);
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNom(), user.getPrenom(),
                user.getRole(), roles.roleName(user.getRole()), user.isActif(),
                user.getDateCreation(), user.getDerniereConnexion());
    }
}
