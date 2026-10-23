package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Append-only trail of every change to a {@link BusinessFact}. */
@Entity
@Table(name = "business_facts_audit")
public class BusinessFactAudit {

    public enum Action { CREATE, UPDATE, DEACTIVATE, REACTIVATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fact_id", nullable = false)
    private Long factId;

    @Column(nullable = false, length = 40)
    private String domain;

    @Column(name = "fact_key", nullable = false, length = 120)
    private String key;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 512)
    private String reason;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public static BusinessFactAudit of(BusinessFact fact, Action action, String oldValue,
                                       String reason, String changedBy) {
        BusinessFactAudit a = new BusinessFactAudit();
        a.factId = fact.getId();
        a.domain = fact.getDomain();
        a.key = fact.getKey();
        a.action = action.name();
        a.oldValue = oldValue;
        a.newValue = fact.getValue();
        a.reason = reason;
        a.changedBy = changedBy;
        return a;
    }

    public Long getId() { return id; }
    public Long getFactId() { return factId; }
    public String getDomain() { return domain; }
    public String getKey() { return key; }
    public String getAction() { return action; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public String getChangedBy() { return changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
