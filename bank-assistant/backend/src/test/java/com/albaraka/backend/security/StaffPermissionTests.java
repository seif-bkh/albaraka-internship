package com.albaraka.backend.security;

import com.albaraka.backend.model.AdminPermission;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.model.User;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffPermissionTests {

    @Test
    void raglabManageExpandsToViewAndRunButDoesNotInventDocumentPermissions() {
        AdminPermission manage = permission("RAGLAB_CONSOLE_MANAGE");
        AdminRole role = AdminRole.custom("CUSTOM_RAGLAB_ADMIN", "RAGLab Admin", null);
        role.updateDefinition("RAGLab Admin", null, Set.of(manage));

        User user = new User();
        user.setEmail("operator@example.test");
        user.setMotDePasse("encoded");
        user.setActif(true);

        UserDetailsImpl principal = UserDetailsImpl.build(user, role);
        Set<String> authorities = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_STAFF"));
        assertTrue(authorities.contains("PERM_RAGLAB_CONSOLE_MANAGE"));
        assertTrue(authorities.contains("PERM_RAGLAB_CONSOLE_VIEW"));
        assertTrue(authorities.contains("PERM_RAGLAB_CONSOLE_RUN"));
        assertFalse(authorities.contains("PERM_DOCUMENTS_READ"));
        assertFalse(authorities.contains("ROLE_SUPER_ADMIN"));
    }

    @Test
    void inactiveRoleDisablesItsStaffPrincipal() {
        AdminRole role = AdminRole.custom("CUSTOM_DISABLED", "Désactivé", null);
        role.setActive(false);
        User user = new User();
        user.setEmail("operator@example.test");
        user.setMotDePasse("encoded");
        user.setActif(true);

        assertFalse(UserDetailsImpl.build(user, role).isEnabled());
    }

    @Test
    void customerPrincipalDoesNotReceiveStaffPermissions() {
        assertEquals(Set.of("ROLE_UTILISATEUR"), UserDetailsImpl.build(customer()).getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet()));
    }

    private static AdminPermission permission(String code) {
        AdminPermission permission = mock(AdminPermission.class);
        when(permission.getCode()).thenReturn(code);
        return permission;
    }

    private static com.albaraka.backend.model.Client customer() {
        com.albaraka.backend.model.Client client = new com.albaraka.backend.model.Client();
        client.setEmail("client@example.test");
        client.setMotDePasse("encoded");
        client.setRole(com.albaraka.backend.model.Role.UTILISATEUR);
        client.setActif(true);
        return client;
    }
}
