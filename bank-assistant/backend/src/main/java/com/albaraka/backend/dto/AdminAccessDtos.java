package com.albaraka.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Request/response records for staff role and access administration. */
public final class AdminAccessDtos {
    private AdminAccessDtos() {
    }

    public record PermissionDto(
            String code,
            String area,
            String name,
            String description,
            int sortOrder) {
    }

    public record RoleDto(
            Long id,
            String code,
            String name,
            String description,
            boolean systemRole,
            boolean protectedRole,
            boolean active,
            long assignedUsers,
            List<String> permissions,
            List<String> effectivePermissions,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record RoleRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotNull @NotEmpty Set<@NotBlank @Size(max = 80) String> permissions) {
    }

    public record RoleActivationRequest(boolean active) {
    }

    public record AuditDto(
            Long id,
            String action,
            String actor,
            String targetRoleCode,
            Long targetUserId,
            String details,
            LocalDateTime createdAt) {
    }
}
