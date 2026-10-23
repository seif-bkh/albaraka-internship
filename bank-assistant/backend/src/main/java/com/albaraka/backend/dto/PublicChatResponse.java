package com.albaraka.backend.dto;

public class PublicChatResponse {
    private String reponse;
    /**
     * Session the answer belongs to. Usually the client's own UUID, but when a
     * request arrives without one the server mints it and the client must adopt
     * it, otherwise every message opens a new (unquota'd) session.
     */
    private String sessionId;
    private Integer tokensConsommes;
    private Integer tokensLimite;
    private Integer nombreMessages;
    private Integer messagesLimite;
    /**
     * Whether the answer was grounded in bank documents. Deliberately the only
     * provenance hint a customer gets: the documents themselves stay in the
     * exchange row for the admin console (see the product rule in
     * {@code AnswerSanitizer}).
     */
    private Boolean grounded;

    public PublicChatResponse(String reponse, Integer tokensConsommes, Integer nombreMessages) {
        this.reponse = reponse;
        this.tokensConsommes = tokensConsommes;
        this.nombreMessages = nombreMessages;
    }

    public String getReponse() {
        return reponse;
    }

    public void setReponse(String reponse) {
        this.reponse = reponse;
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

    public Integer getTokensLimite() {
        return tokensLimite;
    }

    public void setTokensLimite(Integer tokensLimite) {
        this.tokensLimite = tokensLimite;
    }

    public Integer getNombreMessages() {
        return nombreMessages;
    }

    public void setNombreMessages(Integer nombreMessages) {
        this.nombreMessages = nombreMessages;
    }

    public Integer getMessagesLimite() {
        return messagesLimite;
    }

    public void setMessagesLimite(Integer messagesLimite) {
        this.messagesLimite = messagesLimite;
    }
    public Boolean getGrounded() { return grounded; }
    public void setGrounded(Boolean grounded) { this.grounded = grounded; }
}
