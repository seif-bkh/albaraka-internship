package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Append-only record of role definitions, permission grants and staff assignments. */
@Entity
@Table(name = "admin_access_audit")
public class AdminAccessAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 48)
    private String action;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(name = "target_role_code", length = 64)
    private String targetRoleCode;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected AdminAccessAudit() {
    }

    public static AdminAccessAudit of(String action, String actor, String roleCode,
                                      Long userId, String details) {
        AdminAccessAudit audit = new AdminAccessAudit();
        audit.action = action;
        audit.actor = actor;
        audit.targetRoleCode = roleCode;
        audit.targetUserId = userId;
        audit.details = details;
        return audit;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getTargetRoleCode() { return targetRoleCode; }
    public Long getTargetUserId() { return targetUserId; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
