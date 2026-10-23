package com.albaraka.backend.dto;

import java.time.LocalDateTime;

public class ChatSessionDTO {
    private String sessionId;
    private LocalDateTime dateCreation;
    private String titre; // The first question asked in this session

    public ChatSessionDTO() {}

    public ChatSessionDTO(String sessionId, LocalDateTime dateCreation, String titre) {
        this.sessionId = sessionId;
        this.dateCreation = dateCreation;
        this.titre = titre;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
}
