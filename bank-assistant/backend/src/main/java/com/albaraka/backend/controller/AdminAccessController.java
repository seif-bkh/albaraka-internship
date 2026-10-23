package com.albaraka.backend.controller;

import com.albaraka.backend.dto.AdminAccessDtos.AuditDto;
import com.albaraka.backend.dto.AdminAccessDtos.PermissionDto;
import com.albaraka.backend.dto.AdminAccessDtos.RoleActivationRequest;
import com.albaraka.backend.dto.AdminAccessDtos.RoleDto;
import com.albaraka.backend.dto.AdminAccessDtos.RoleRequest;
import com.albaraka.backend.services.AdminAccessAuditService;
import com.albaraka.backend.services.AdminRoleService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Super-Admin-only API for fixed permissions, dynamic roles and access audit history. */
@RestController
@RequestMapping("/api/admin/access-control")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class AdminAccessController {
    private final AdminRoleService roles;
    private final AdminAccessAuditService audit;

    public AdminAccessController(AdminRoleService roles, AdminAccessAuditService audit) {
        this.roles = roles;
        this.audit = audit;
    }

    @GetMapping("/permissions")
    public List<PermissionDto> permissions() {
        return roles.listSupportedPermissions();
    }

    @GetMapping("/roles")
    public List<RoleDto> listRoles() {
        return roles.listStaffRoles();
    }

    @PostMapping("/roles")
    public RoleDto createRole(@Valid @RequestBody RoleRequest request, Authentication authentication) {
        return roles.createRole(request, authentication.getName());
    }

    @PutMapping("/roles/{id}")
    public RoleDto updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request,
                              Authentication authentication) {
        return roles.updateRole(id, request, authentication.getName());
    }

    @PatchMapping("/roles/{id}/active")
    public RoleDto setRoleActive(@PathVariable Long id, @Valid @RequestBody RoleActivationRequest request,
                                 Authentication authentication) {
        return roles.setRoleActive(id, request.active(), authentication.getName());
    }

    @GetMapping("/audit")
    public List<AuditDto> audit() {
        return audit.latest();
    }
}
