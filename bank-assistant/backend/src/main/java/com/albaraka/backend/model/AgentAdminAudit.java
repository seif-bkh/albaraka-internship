package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Append-only journal of everything an admin changed about the assistant:
 * the engine's address, and (in later slices) the serving engine itself, model
 * or key changes, index rebuilds and evaluations.
 *
 * <p>Never contains secret values — only what was changed, where, by whom and
 * why.
 */
@Entity
@Table(name = "agent_admin_audit")
public class AgentAdminAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(length = 120)
    private String target;

    @Column(length = 600)
    private String detail;

    @Column(length = 512)
    private String reason;

    @Column(length = 255)
    private String actor;

    @Column(nullable = false, length = 20)
    private String outcome = "OK";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AgentAdminAudit() {
    }

    public static AgentAdminAudit of(String action, String target, String detail,
                                     String reason, String actor, String outcome) {
        AgentAdminAudit entry = new AgentAdminAudit();
        entry.action = action;
        entry.target = target;
        entry.detail = detail;
        entry.reason = reason;
        entry.actor = actor;
        entry.outcome = outcome == null || outcome.isBlank() ? "OK" : outcome;
        return entry;
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getTarget() { return target; }
    public String getDetail() { return detail; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public String getOutcome() { return outcome; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
