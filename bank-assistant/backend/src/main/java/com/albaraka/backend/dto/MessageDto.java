package com.albaraka.backend.dto;

import com.albaraka.backend.model.MessageEntity;
import com.albaraka.backend.services.ChatTraceDocuments;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO pour un message individuel dans le fil de conversation (vue admin).
 *
 * <p>Le client ne voit que {@code question} / {@code reponse} ; l'agent qui audite
 * l'assistant voit aussi ce qui a servi à construire la réponse : les documents
 * consultés ({@code sourcesUtilisees} = noms de fichiers d'ingestion, jamais les
 * alias {@code S1 S2} d'une citation) et le bloc {@code observability} — modèle,
 * jetons, latence, horodatages réels de la question et de la réponse, état.
 */
public class MessageDto {
    private Long id;
    private String question;
    private String questionReformulee;
    private String intention;
    private String reponse;
    private List<String> sourcesUtilisees;
    /** Tout ce que la recherche a consulté, y compris les passages écartés par le seuil. */
    private List<ChatTraceDocuments.DocumentRef> documents;
    private Boolean ragUtilise;
    private LocalDateTime dateCreation;
    private Observability observability;

    /**
     * The audit half of one stored exchange. Its own type because the fields are
     * read together ("was it slow, expensive, and grounded?" is one question) and
     * because a 20-argument constructor is where mapping mistakes hide.
     */
    public record Observability(LocalDateTime askedAt, LocalDateTime answeredAt, String model,
                                Integer tokensIn, Integer tokensOut, Integer latencyMs,
                                String status, Boolean grounded, String langue, String domaine,
                                Long userId, Boolean hasTrace) {

        /**
         * Public because the client-history endpoint maps the same row from another
         * package; the mapping itself stays here, so a DTO has exactly one place
         * where an exchange becomes what a reviewer reads.
         */
        public static Observability of(MessageEntity m) {
            return new Observability(m.getAskedAt(), m.getAnsweredAt(), m.getModel(),
                    m.getTokensIn(), m.getTokensOut(), m.getLatencyMs(), m.getStatus(),
                    m.getGrounded(), m.getLangue(), m.getDomaine(), m.getUserId(),
                    m.getTraceJson() != null && !m.getTraceJson().isBlank());
        }
    }

    public MessageDto(Long id, String question, String questionReformulee,
                      String intention, String reponse, List<String> sourcesUtilisees,
                      Boolean ragUtilise, LocalDateTime dateCreation) {
        this(id, question, questionReformulee, intention, reponse, sourcesUtilisees, ragUtilise,
                dateCreation, List.of(), null);
    }

    public MessageDto(Long id, String question, String questionReformulee,
                      String intention, String reponse, List<String> sourcesUtilisees,
                      Boolean ragUtilise, LocalDateTime dateCreation,
                      List<ChatTraceDocuments.DocumentRef> documents,
                      Observability observability) {
        this.id = id;
        this.question = question;
        this.questionReformulee = questionReformulee;
        this.intention = intention;
        this.reponse = reponse;
        this.sourcesUtilisees = sourcesUtilisees != null ? sourcesUtilisees : List.of();
        this.ragUtilise = ragUtilise != null ? ragUtilise : false;
        this.dateCreation = dateCreation;
        this.documents = documents != null ? documents : List.of();
        this.observability = observability;
    }

    public Long getId() { return id; }
    public String getQuestion() { return question; }
    public String getQuestionReformulee() { return questionReformulee; }
    public String getIntention() { return intention; }
    public String getReponse() { return reponse; }
    public List<String> getSourcesUtilisees() { return sourcesUtilisees; }
    public List<ChatTraceDocuments.DocumentRef> getDocuments() { return documents; }
    public Boolean getRagUtilise() { return ragUtilise; }
    public LocalDateTime dateCreation() { return dateCreation; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public Observability getObservability() { return observability; }
}
