package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * The admin-chosen base URL of the external RAG service.
 *
 * <p>Newest row wins; no row means "use the deployed default". Rows are never
 * updated in place, so the table doubles as a small history of addresses the
 * assistant has pointed at (the journal in {@link AgentAdminAudit} carries the
 * who/why).
 */
@Entity
@Table(name = "agent_endpoint")
public class AgentEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String url;

    @Column(length = 512)
    private String reason;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public AgentEndpoint() {
    }

    public AgentEndpoint(String url, String reason, String updatedBy) {
        this.url = url;
        this.reason = reason;
        this.updatedBy = updatedBy;
    }

    public Long getId() { return id; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
