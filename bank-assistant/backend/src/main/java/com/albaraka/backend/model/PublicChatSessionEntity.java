package com.albaraka.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "public_chat_session")
public class PublicChatSessionEntity {

    @Id
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "tokens_consommes")
    private Integer tokensConsommes = 0;

    @Column(name = "nombre_messages")
    private Integer nombreMessages = 0;

    @Column(name = "langue")
    private String langue;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "date_derniere_activite")
    private LocalDateTime dateDerniereActivite;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_id")
    private Long userId;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
        dateDerniereActivite = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateDerniereActivite = LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getTokensConsommes() {
        return tokensConsommes;
    }

    public void setTokensConsommes(Integer tokensConsommes) {
        this.tokensConsommes = tokensConsommes;
    }

    public Integer getNombreMessages() {
        return nombreMessages;
    }

    public void setNombreMessages(Integer nombreMessages) {
        this.nombreMessages = nombreMessages;
    }

    public String getLangue() {
        return langue;
    }

    public void setLangue(String langue) {
        this.langue = langue;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public LocalDateTime getDateDerniereActivite() {
        return dateDerniereActivite;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
