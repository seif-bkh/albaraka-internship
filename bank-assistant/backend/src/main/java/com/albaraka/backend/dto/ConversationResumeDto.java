package com.albaraka.backend.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO résumé d'une conversation : statistiques clés sans données sensibles.
 */
public class ConversationResumeDto {
    private Long conversationId;
    private String langue;
    private Integer nombreMessages;
    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private Long dureeTotaleMinutes;
    private List<String> sourcesUniques;  // Liste dédupliquée de tous les docs utilisés
    private String adresseIpMasquee;      // IP partielle (3 premiers octets)

    public ConversationResumeDto(Long conversationId, String langue, Integer nombreMessages,
                                  LocalDateTime dateDebut, LocalDateTime dateFin,
                                  String adresseIp, List<String> sourcesUniques) {
        this.conversationId = conversationId;
        this.langue = langue;
        this.nombreMessages = nombreMessages != null ? nombreMessages : 0;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.dureeTotaleMinutes = (dateDebut != null && dateFin != null)
            ? Duration.between(dateDebut, dateFin).toMinutes()
            : 0L;
        this.sourcesUniques = sourcesUniques;
        // Masquer la dernière partie de l'IP: 192.168.1.X → 192.168.1.*
        if (adresseIp != null && adresseIp.contains(".")) {
            int lastDot = adresseIp.lastIndexOf('.');
            this.adresseIpMasquee = adresseIp.substring(0, lastDot) + ".*";
        } else {
            this.adresseIpMasquee = "***";
        }
    }

    public Long getConversationId() { return conversationId; }
    public String getLangue() { return langue; }
    public Integer getNombreMessages() { return nombreMessages; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public Long getDureeTotaleMinutes() { return dureeTotaleMinutes; }
    public List<String> getSourcesUniques() { return sourcesUniques; }
    public String getAdresseIpMasquee() { return adresseIpMasquee; }
}
