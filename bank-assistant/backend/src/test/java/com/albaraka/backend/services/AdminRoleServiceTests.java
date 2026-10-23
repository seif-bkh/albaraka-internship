package com.albaraka.backend.services;

import com.albaraka.backend.dto.AdminAccessDtos.RoleRequest;
import com.albaraka.backend.model.AdminPermission;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.repository.AdminPermissionRepository;
import com.albaraka.backend.repository.AdminRoleRepository;
import com.albaraka.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminRoleServiceTests {
    private AdminRoleRepository roles;
    private AdminPermissionRepository permissions;
    private UserRepository users;
    private AdminAccessAuditService audit;
    private AdminRoleService service;

    @BeforeEach
    void setUp() {
        roles = mock(AdminRoleRepository.class);
        permissions = mock(AdminPermissionRepository.class);
        users = mock(UserRepository.class);
        audit = mock(AdminAccessAuditService.class);
        service = new AdminRoleService(roles, permissions, users, audit);
    }

    @Test
    void rejectsPermissionKeysOutsideTheFixedCatalogueBeforeWriting() {
        RoleRequest request = new RoleRequest("Arbitrary", null,
                Set.of("DOCUMENTS_READ", "CAN_APPROVE_TRANSFERS"));

        assertThrows(IllegalArgumentException.class, () -> service.createRole(request, "root"));
        verify(permissions, never()).findByCodeInOrderBySortOrderAsc(anySet());
        verify(roles, never()).save(any());
    }

    @Test
    void rejectsRolesWithNoSupportedPermissions() {
        RoleRequest request = new RoleRequest("Empty", null, Set.of());

        assertThrows(IllegalArgumentException.class, () -> service.createRole(request, "root"));
        verify(permissions, never()).findByCodeInOrderBySortOrderAsc(anySet());
    }

    @Test
    void requiresViewWhenAStaffRoleCanRunRaglabRequests() {
        RoleRequest request = new RoleRequest("Runner", null, Set.of("RAGLAB_CONSOLE_RUN"));

        assertThrows(IllegalArgumentException.class, () -> service.createRole(request, "root"));
        verify(permissions, never()).findByCodeInOrderBySortOrderAsc(anySet());
    }

    @Test
    void manageIncludesViewAndRunInTheEffectiveRoleSummary() {
        AdminPermission manage = permission("RAGLAB_CONSOLE_MANAGE");
        AdminRole role = AdminRole.custom("CUSTOM_RAGLAB", "RAGLab manager", null);
        role.updateDefinition("RAGLab manager", null, Set.of(manage));
        when(roles.findAllStaffWithPermissions()).thenReturn(List.of(role));
        when(users.countByRole("CUSTOM_RAGLAB")).thenReturn(1L);

        var dto = service.listStaffRoles().getFirst();

        assertEquals(List.of("RAGLAB_CONSOLE_MANAGE"), dto.permissions());
        assertTrue(dto.effectivePermissions().contains("RAGLAB_CONSOLE_VIEW"));
        assertTrue(dto.effectivePermissions().contains("RAGLAB_CONSOLE_RUN"));
    }

    @Test
    void assignmentLocksAndRejectsADeactivatedRole() {
        AdminRole role = AdminRole.custom("CUSTOM_DISABLED", "Disabled", null);
        role.setActive(false);
        when(roles.findByCodeForUpdate("CUSTOM_DISABLED")).thenReturn(Optional.of(role));

        assertThrows(ResponseStatusException.class,
                () -> service.requireAssignableStaffRole("CUSTOM_DISABLED"));
        verify(roles).findByCodeForUpdate("CUSTOM_DISABLED");
    }

    @Test
    void deactivationLocksTheRoleAndRejectsRolesStillAssignedToActiveStaff() {
        AdminRole role = AdminRole.custom("CUSTOM_IN_USE", "In use", null);
        when(roles.findByIdForUpdate(42L)).thenReturn(Optional.of(role));
        when(users.countByRoleAndActifTrue("CUSTOM_IN_USE")).thenReturn(1L);

        assertThrows(ResponseStatusException.class, () -> service.setRoleActive(42L, false, "root"));
        verify(roles).findByIdForUpdate(42L);
        verify(users).countByRoleAndActifTrue("CUSTOM_IN_USE");
    }

    @Test
    void staffRoleAdministrationCannotModifyCustomerPortalRoles() {
        AdminRole customerRole = mock(AdminRole.class);
        when(customerRole.isStaffRole()).thenReturn(false);
        when(customerRole.getCode()).thenReturn("UTILISATEUR");
        when(roles.findByIdForUpdate(99L)).thenReturn(Optional.of(customerRole));
        RoleRequest request = new RoleRequest("Customer role", null, Set.of("DOCUMENTS_READ"));

        assertThrows(ResponseStatusException.class, () -> service.updateRole(99L, request, "root"));
        assertThrows(ResponseStatusException.class, () -> service.setRoleActive(99L, false, "root"));
        verify(permissions, never()).findByCodeInOrderBySortOrderAsc(anySet());
    }

    @Test
    void superAdminMembershipGuardUsesItsStableDatabaseLockRow() {
        AdminRole superAdmin = AdminRole.custom("SUPER_ADMIN", "Super Administrateur", null);
        when(roles.findByCodeForUpdate("SUPER_ADMIN")).thenReturn(Optional.of(superAdmin));

        service.lockSuperAdminMembership();

        verify(roles).findByCodeForUpdate("SUPER_ADMIN");
    }

    private static AdminPermission permission(String code) {
        AdminPermission permission = mock(AdminPermission.class);
        when(permission.getCode()).thenReturn(code);
        when(permission.getSortOrder()).thenReturn(90);
        return permission;
    }
}
