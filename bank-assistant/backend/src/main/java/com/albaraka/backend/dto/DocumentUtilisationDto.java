package com.albaraka.backend.dto;

import java.time.LocalDateTime;

/**
 * DTO pour la vue inverse "gouvernance document" :
 * quelles conversations ont utilisé ce document comme source ?
 */
public class DocumentUtilisationDto {
    private Long messageId;
    private Long conversationId;
    private String sessionIdTronque;
    private String question;
    private String intention;
    private LocalDateTime dateCreation;

    public DocumentUtilisationDto(Long messageId, Long conversationId, String sessionId,
                                   String question, String intention, LocalDateTime dateCreation) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.sessionIdTronque = sessionId != null
            ? sessionId.substring(0, Math.min(8, sessionId.length())) + "..."
            : "N/A";
        this.question = question;
        this.intention = intention;
        this.dateCreation = dateCreation;
    }

    public Long getMessageId() { return messageId; }
    public Long getConversationId() { return conversationId; }
    public String getSessionIdTronque() { return sessionIdTronque; }
    public String getQuestion() { return question; }
    public String getIntention() { return intention; }
    public LocalDateTime getDateCreation() { return dateCreation; }
}
