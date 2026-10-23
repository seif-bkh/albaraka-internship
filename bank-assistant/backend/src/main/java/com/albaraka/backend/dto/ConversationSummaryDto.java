package com.albaraka.backend.dto;

import java.time.LocalDateTime;

/**
 * DTO pour la liste paginée des conversations.
 * N'expose PAS l'adresse IP complète (sécurité RGPD).
 */
public class ConversationSummaryDto {
    private Long id;
    private String sessionIdTronque;  // 8 premiers caractères seulement
    private String langue;
    private Integer nombreMessages;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private String localisation;

    public ConversationSummaryDto(Long id, String sessionId, String langue,
                                   Integer nombreMessages, LocalDateTime dateDebut,
                                   LocalDateTime dateFin, String localisation) {
        this.id = id;
        // Tronquer le sessionId pour anonymisation dans la liste
        this.sessionIdTronque = sessionId != null
            ? sessionId.substring(0, Math.min(8, sessionId.length())) + "..."
            : "N/A";
        this.langue = langue;
        this.nombreMessages = nombreMessages != null ? nombreMessages : 0;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.localisation = localisation;
    }

    public Long getId() { return id; }
    public String getSessionIdTronque() { return sessionIdTronque; }
    public String getLangue() { return langue; }
    public Integer getNombreMessages() { return nombreMessages; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public String getLocalisation() { return localisation; }
}
