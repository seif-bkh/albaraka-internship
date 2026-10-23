package com.albaraka.backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A client request to meet (APPOINTMENT) or be called back (CALLBACK) by an
 * advisor — created from the portal-home quick tiles. It is a request, not a
 * booking: {@code status} starts at EN_ATTENTE; staff confirm out of band.
 */
@Entity
@Table(name = "advisor_requests")
public class AdvisorRequest {

    public enum Kind { APPOINTMENT, CALLBACK }

    public enum Status { EN_ATTENTE, CONFIRMEE, ANNULEE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.EN_ATTENTE;

    @Column
    private String motif;

    /** AGENCE | TELEPHONE — only for APPOINTMENT. */
    @Column(name = "meeting_method")
    private String meetingMethod;

    /** Free text — no branch catalogue exists in governed data yet (P1). */
    @Column(name = "preferred_branch")
    private String preferredBranch;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /** "HH:mm" slot — APPOINTMENT time or CALLBACK preferred moment. */
    @Column(name = "preferred_time")
    private String preferredTime;

    @Column
    private String telephone;

    @Column
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.EN_ATTENTE;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getMeetingMethod() { return meetingMethod; }
    public void setMeetingMethod(String meetingMethod) { this.meetingMethod = meetingMethod; }
    public String getPreferredBranch() { return preferredBranch; }
    public void setPreferredBranch(String preferredBranch) { this.preferredBranch = preferredBranch; }
    public LocalDate getPreferredDate() { return preferredDate; }
    public void setPreferredDate(LocalDate preferredDate) { this.preferredDate = preferredDate; }
    public String getPreferredTime() { return preferredTime; }
    public void setPreferredTime(String preferredTime) { this.preferredTime = preferredTime; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
