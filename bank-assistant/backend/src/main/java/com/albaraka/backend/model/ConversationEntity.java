package com.albaraka.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A chat conversation row in {@code conversations}, one per {@code session_id}.
 *
 * <p>Upserted by {@code session_id} (mirroring the legacy {@code api.py} UPSERT):
 * on a new session we insert {@code date_debut=date_fin=now, nombre_messages=1};
 * on an existing session we bump {@code date_fin} and {@code nombre_messages}.
 * The {@code messages} children reference this row via {@code conversation_id}.
 * Java owns writes from Phase 3.2 onward.
 */
@Entity
@Table(name = "conversations")
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "langue")
    private String langue;

    @Column(name = "adresse_ip")
    private String adresseIp;

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @Column(name = "nombre_messages")
    private Integer nombreMessages;

    @Column(name = "localisation")
    private String localisation;

    /** Owning bank client when the exchange was authenticated (null = anonymous). */
    @Column(name = "client_id")
    private Long clientId;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    private List<MessageEntity> messages;

    // Getters
    public Long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getLangue() { return langue; }
    public String getAdresseIp() { return adresseIp; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public LocalDateTime getDateFin() { return dateFin; }
    public Integer getNombreMessages() { return nombreMessages; }
    public String getLocalisation() { return localisation; }
    public List<MessageEntity> getMessages() { return messages; }

    // Setters (Java owns writes from Phase 3.2 onward)
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setLangue(String langue) { this.langue = langue; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }
    public void setDateFin(LocalDateTime dateFin) { this.dateFin = dateFin; }
    public void setNombreMessages(Integer nombreMessages) { this.nombreMessages = nombreMessages; }
    public void setLocalisation(String localisation) { this.localisation = localisation; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
}
