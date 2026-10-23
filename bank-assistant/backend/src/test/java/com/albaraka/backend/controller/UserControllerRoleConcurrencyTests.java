package com.albaraka.backend.controller;

import com.albaraka.backend.dto.StaffUserDtos.UpdateUserRequest;
import com.albaraka.backend.model.AdminRole;
import com.albaraka.backend.model.User;
import com.albaraka.backend.repository.ClientRepository;
import com.albaraka.backend.repository.UserRepository;
import com.albaraka.backend.services.AdminAccessAuditService;
import com.albaraka.backend.services.AdminRoleService;
import com.albaraka.backend.services.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerRoleConcurrencyTests {
    private static final Long LAST_SUPER_ADMIN_ID = 17L;

    private UserRepository users;
    private AdminRoleService roles;
    private UserController controller;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        ClientRepository clients = mock(ClientRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
        roles = mock(AdminRoleService.class);
        AdminAccessAuditService audit = mock(AdminAccessAuditService.class);
        controller = new UserController(users, clients, passwords, refreshTokens, roles, audit);

        User superAdmin = new User();
        superAdmin.setId(LAST_SUPER_ADMIN_ID);
        superAdmin.setEmail("root@example.test");
        superAdmin.setRole("SUPER_ADMIN");
        superAdmin.setActif(true);
        when(users.findByIdForUpdate(LAST_SUPER_ADMIN_ID)).thenReturn(Optional.of(superAdmin));
        when(users.countByRoleAndActifTrue("SUPER_ADMIN")).thenReturn(1L);
    }

    @Test
    void lastSuperAdminDemotionTakesStableMembershipLockAndIsRejected() {
        when(roles.requireAssignableStaffRole("VIEWER"))
                .thenReturn(AdminRole.custom("VIEWER", "Lecteur", null));

        assertThrows(ResponseStatusException.class, () -> controller.updateUser(LAST_SUPER_ADMIN_ID,
                new UpdateUserRequest("Root", "Admin", "VIEWER", true, null), authentication()));

        verify(users).findByIdForUpdate(LAST_SUPER_ADMIN_ID);
        verify(roles).lockSuperAdminMembership();
        verify(users).countByRoleAndActifTrue("SUPER_ADMIN");
    }

    @Test
    void lastSuperAdminDeletionTakesStableMembershipLockAndIsRejected() {
        assertThrows(ResponseStatusException.class,
                () -> controller.deleteUser(LAST_SUPER_ADMIN_ID, authentication()));

        verify(users).findByIdForUpdate(LAST_SUPER_ADMIN_ID);
        verify(roles).lockSuperAdminMembership();
        verify(users).countByRoleAndActifTrue("SUPER_ADMIN");
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken("root@example.test", "n/a");
    }
}
