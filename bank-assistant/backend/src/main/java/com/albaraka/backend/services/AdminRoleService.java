package com.albaraka.backend.services;

import com.albaraka.backend.dto.AdminAccessDtos.PermissionDto;
import com.albaraka.backend.dto.AdminAccessDtos.RoleDto;
import com.albaraka.backend.dto.AdminAccessDtos.RoleRequest;
import com.albaraka.backend.model.AdminPermission;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.repository.AdminPermissionRepository;
import com.albaraka.backend.repository.AdminRoleRepository;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.security.AdminPermissionCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminRoleService {
    private static final Set<String> DOCUMENT_MUTATIONS = Set.of(
            AdminPermissionCodes.DOCUMENTS_CREATE,
            AdminPermissionCodes.DOCUMENTS_UPDATE,
            AdminPermissionCodes.DOCUMENTS_DELETE,
            AdminPermissionCodes.DOCUMENTS_PROCESS);

    private final AdminRoleRepository roleRepository;
    private final AdminPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AdminAccessAuditService audit;

    public AdminRoleService(AdminRoleRepository roleRepository,
                            AdminPermissionRepository permissionRepository,
                            UserRepository userRepository,
                            AdminAccessAuditService audit) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> listSupportedPermissions() {
        return permissionRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(permission -> AdminPermissionCodes.ALL.contains(permission.getCode()))
                .map(this::toPermissionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> listStaffRoles() {
        return roleRepository.findAllStaffWithPermissions().stream()
                .map(this::toRoleDto)
                .toList();
    }

    @Transactional
    public RoleDto createRole(RoleRequest request, String actor) {
        Set<AdminPermission> permissions = resolvePermissions(request.permissions());
        String code = generateCode();
        String name = cleanName(request.name());
        String description = cleanDescription(request.description());
        AdminRole role = AdminRole.custom(code, name, description);
        role.updateDefinition(name, description, permissions);
        AdminRole saved = roleRepository.save(role);
        audit.record("ROLE_CREATED", actor, code, null,
                "Nom=" + name + "; permissions=" + permissionCodes(saved.getPermissions()));
        return toRoleDto(saved);
    }

    @Transactional
    public RoleDto updateRole(Long id, RoleRequest request, String actor) {
        AdminRole role = findRoleForUpdate(id);
        if (!role.isStaffRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les rôles du portail client ne sont pas modifiables depuis l’administration du personnel.");
        }
        if (isProtectedSuperAdmin(role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le rôle Super Administrateur est protégé et ne peut pas être modifié.");
        }
        Set<AdminPermission> permissions = resolvePermissions(request.permissions());
        String beforeName = role.getName();
        String beforeDescription = role.getDescription();
        String beforePermissions = permissionCodes(role.getPermissions()).toString();
        String name = cleanName(request.name());
        String description = cleanDescription(request.description());
        role.updateDefinition(name, description, permissions);
        AdminRole saved = roleRepository.save(role);
        audit.record("ROLE_UPDATED", actor, saved.getCode(), null,
                "Nom avant=" + beforeName + "; après=" + name
                        + "; description avant=" + beforeDescription + "; après=" + description
                        + "; permissions avant=" + beforePermissions
                        + "; après=" + permissionCodes(saved.getPermissions()));
        return toRoleDto(saved);
    }

    @Transactional
    public RoleDto setRoleActive(Long id, boolean active, String actor) {
        AdminRole role = findRoleForUpdate(id);
        if (!role.isStaffRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les rôles du portail client ne sont pas modifiables depuis l’administration du personnel.");
        }
        if (isProtectedSuperAdmin(role) && !active) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le rôle Super Administrateur ne peut pas être désactivé.");
        }
        if (!active && role.isSystemRole()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Les rôles historiques du système ne peuvent pas être désactivés.");
        }
        if (!active && userRepository.countByRoleAndActifTrue(role.getCode()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Réaffectez les comptes actifs avant de désactiver ce rôle.");
        }
        boolean changed = role.isActive() != active;
        role.setActive(active);
        AdminRole saved = roleRepository.save(role);
        if (changed) {
            audit.record(active ? "ROLE_ACTIVATED" : "ROLE_DEACTIVATED", actor,
                    saved.getCode(), null, "Rôle " + (active ? "activé" : "désactivé"));
        }
        return toRoleDto(saved);
    }

    @Transactional
    public AdminRole requireAssignableStaffRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new IllegalArgumentException("Un rôle du personnel est requis.");
        }
        AdminRole role = roleRepository.findByCodeForUpdate(roleCode.trim())
                .filter(AdminRole::isStaffRole)
                .orElseThrow(() -> new IllegalArgumentException("Rôle du personnel inconnu."));
        if (!role.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce rôle est désactivé.");
        }
        return role;
    }

    /**
     * A stable row lock that serializes every mutation of active Super Admin
     * membership. Call inside the same transaction as the membership change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSuperAdminMembership() {
        roleRepository.findByCodeForUpdate("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("Le rôle Super Administrateur est absent."));
    }

    @Transactional(readOnly = true)
    public String roleName(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .map(AdminRole::getName)
                .orElse(roleCode);
    }

    private AdminRole findRoleForUpdate(Long id) {
        return roleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rôle introuvable."));
    }

    private Set<AdminPermission> resolvePermissions(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins une permission.");
        }
        Set<String> codes = requested.stream()
                .filter(code -> code != null)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("Sélectionnez au moins une permission prise en charge.");
        }
        Set<String> unknown = new HashSet<>(codes);
        unknown.removeAll(AdminPermissionCodes.ALL);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Permission non prise en charge: " + unknown);
        }
        if (codes.stream().anyMatch(DOCUMENT_MUTATIONS::contains)
                && !codes.contains(AdminPermissionCodes.DOCUMENTS_READ)) {
            throw new IllegalArgumentException(
                    "Les droits de création, modification, suppression ou traitement requièrent la lecture des documents.");
        }
        if (codes.contains(AdminPermissionCodes.RAGLAB_RUN)
                && !codes.contains(AdminPermissionCodes.RAGLAB_VIEW)
                && !codes.contains(AdminPermissionCodes.RAGLAB_MANAGE)) {
            throw new IllegalArgumentException("Le droit RAGLab Exécuter requiert aussi Voir.");
        }

        List<AdminPermission> found = permissionRepository.findByCodeInOrderBySortOrderAsc(codes).stream()
                .filter(permission -> AdminPermissionCodes.ALL.contains(permission.getCode()))
                .toList();
        Set<String> foundCodes = found.stream().map(AdminPermission::getCode).collect(Collectors.toSet());
        if (!foundCodes.equals(codes)) {
            Set<String> missing = new HashSet<>(codes);
            missing.removeAll(foundCodes);
            throw new IllegalArgumentException("Permissions indisponibles dans le catalogue: " + missing);
        }
        return new HashSet<>(found);
    }

    private RoleDto toRoleDto(AdminRole role) {
        List<String> direct = permissionCodes(role.getPermissions());
        Set<String> effective = new HashSet<>(direct);
        if (effective.contains(AdminPermissionCodes.RAGLAB_MANAGE)) {
            effective.add(AdminPermissionCodes.RAGLAB_VIEW);
            effective.add(AdminPermissionCodes.RAGLAB_RUN);
        }
        List<String> effectiveOrdered = new ArrayList<>(effective);
        effectiveOrdered.sort(Comparator.comparingInt(this::permissionSortOrder));
        return new RoleDto(
                role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.isSystemRole(), isProtectedSuperAdmin(role), role.isActive(),
                userRepository.countByRole(role.getCode()), direct, effectiveOrdered,
                role.getCreatedAt(), role.getUpdatedAt());
    }

    private int permissionSortOrder(String code) {
        int index = AdminPermissionCodes.ORDERED.indexOf(code);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private List<String> permissionCodes(Set<AdminPermission> permissions) {
        return permissions.stream()
                .map(AdminPermission::getCode)
                .filter(AdminPermissionCodes.ALL::contains)
                .sorted(Comparator.comparingInt(this::permissionSortOrder))
                .toList();
    }

    private PermissionDto toPermissionDto(AdminPermission permission) {
        return new PermissionDto(permission.getCode(), permission.getArea(), permission.getName(),
                permission.getDescription(), permission.getSortOrder());
    }

    private String generateCode() {
        String code;
        do {
            code = "CUSTOM_" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase(Locale.ROOT);
        } while (roleRepository.existsByCode(code));
        return code;
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Le nom du rôle est requis.");
        return name.trim();
    }

    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) return null;
        return description.trim();
    }

    private boolean isProtectedSuperAdmin(AdminRole role) {
        return "SUPER_ADMIN".equals(role.getCode());
    }
}
