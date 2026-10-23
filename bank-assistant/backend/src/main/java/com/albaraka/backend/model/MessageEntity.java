package com.albaraka.backend.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

import java.time.LocalDateTime;

/**
 * A single chat exchange row in {@code messages}.
 *
 * <p>One row holds both halves of an exchange: the {@code question} (as sent by
 * the user), the reformulation/intent, and the {@code reponse} (as returned by the
 * agent) with its sources. This mirrors exactly what the legacy Python pipeline
 * used to insert (see {@code api.py::persister_echange}), so history rendering and
 * the statistics dashboards keep working unchanged after the ownership handover.
 *
 * <p>{@link PublicChatSessionEntity} remains the quota/metering row; the
 * {@code conversations}/{@code messages} tables become Java-owned (Phase 3.2).
 */
@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private ConversationEntity conversation;

    @Column(name = "question", columnDefinition = "TEXT")
    private String question;

    @Column(name = "question_reformulee", columnDefinition = "TEXT")
    private String questionReformulee;

    @Column(name = "intention", length = 50)
    private String intention;

    @Column(name = "reponse", columnDefinition = "TEXT")
    private String reponse;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "sources_utilisees", columnDefinition = "text ARRAY")
    private List<String> sourcesUtilisees;

    @Column(name = "rag_utilise")
    private Boolean ragUtilise;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    // --- Monitoring (V7): why the agent answered the way it did -----------------
    @Column(name = "grounded")
    private Boolean grounded;

    @Column(name = "domaine", length = 40)
    private String domaine;

    @Column(name = "langue", length = 8)
    private String langue;

    @Column(name = "trace_json", columnDefinition = "TEXT")
    private String traceJson;

    // --- Real-time Observability (V13): latency, tokens, timestamps, user & model ---
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "model", length = 64)
    private String model;

    @Column(name = "tokens_in")
    private Integer tokensIn;

    @Column(name = "tokens_out")
    private Integer tokensOut;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "asked_at")
    private LocalDateTime askedAt;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "status", length = 20)
    private String status;

    // Getters
    public Long getId() { return id; }
    public ConversationEntity getConversation() { return conversation; }
    public String getQuestion() { return question; }
    public String getQuestionReformulee() { return questionReformulee; }
    public String getIntention() { return intention; }
    public String getReponse() { return reponse; }
    public List<String> getSourcesUtilisees() { return sourcesUtilisees; }
    public Boolean getRagUtilise() { return ragUtilise; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public Boolean getGrounded() { return grounded; }
    public String getDomaine() { return domaine; }
    public String getLangue() { return langue; }
    public String getTraceJson() { return traceJson; }
    public Long getUserId() { return userId; }
    public String getModel() { return model; }
    public Integer getTokensIn() { return tokensIn; }
    public Integer getTokensOut() { return tokensOut; }
    public Integer getLatencyMs() { return latencyMs; }
    public LocalDateTime getAskedAt() { return askedAt; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public String getStatus() { return status; }

    // Setters (Java owns writes from Phase 3.2 onward)
    public void setConversation(ConversationEntity conversation) { this.conversation = conversation; }
    public void setQuestion(String question) { this.question = question; }
    public void setQuestionReformulee(String questionReformulee) { this.questionReformulee = questionReformulee; }
    public void setIntention(String intention) { this.intention = intention; }
    public void setReponse(String reponse) { this.reponse = reponse; }
    public void setSourcesUtilisees(List<String> sourcesUtilisees) { this.sourcesUtilisees = sourcesUtilisees; }
    public void setRagUtilise(Boolean ragUtilise) { this.ragUtilise = ragUtilise; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public void setGrounded(Boolean grounded) { this.grounded = grounded; }
    public void setDomaine(String domaine) { this.domaine = domaine; }
    public void setLangue(String langue) { this.langue = langue; }
    public void setTraceJson(String traceJson) { this.traceJson = traceJson; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setModel(String model) { this.model = model; }
    public void setTokensIn(Integer tokensIn) { this.tokensIn = tokensIn; }
    public void setTokensOut(Integer tokensOut) { this.tokensOut = tokensOut; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public void setAskedAt(LocalDateTime askedAt) { this.askedAt = askedAt; }
    public void setAnsweredAt(LocalDateTime answeredAt) { this.answeredAt = answeredAt; }
    public void setStatus(String status) { this.status = status; }
}
