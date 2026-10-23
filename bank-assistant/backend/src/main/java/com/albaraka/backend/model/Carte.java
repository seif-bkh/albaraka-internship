package com.albaraka.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** A payment card of a {@link Client} (portal demo data — see V8__client_portal). */
@Entity
@Table(name = "cartes")
public class Carte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "compte_id")
    private Long compteId;

    /** EVASION, GOLD_NATIONALE, PLATINUM… */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String reseau = "MASTERCARD";

    /** Masked card number, e.g. "4571 **** **** 6842". */
    @Column(nullable = false)
    private String porteur;

    /** MM/yy. */
    @Column(nullable = false)
    private String expiration;

    @Column(name = "plafond_journalier", nullable = false, precision = 12, scale = 3)
    private BigDecimal plafondJournalier = new BigDecimal("1000.000");

    @Column(nullable = false)
    private boolean bloque = false;

    /** DEBIT | CREDIT | PREPAID | PREMIUM. */
    @Column(nullable = false, length = 16)
    private String categorie = "DEBIT";

    /** ACTIVE | BLOQUEE | OPPOSITION | EXPIREE. */
    @Column(nullable = false, length = 20)
    private String statut = "ACTIVE";

    @Column(name = "plafond_retrait", nullable = false, precision = 12, scale = 3)
    private BigDecimal plafondRetrait = new BigDecimal("400.000");

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Long getCompteId() { return compteId; }
    public void setCompteId(Long compteId) { this.compteId = compteId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReseau() { return reseau; }
    public void setReseau(String reseau) { this.reseau = reseau; }
    public String getPorteur() { return porteur; }
    public void setPorteur(String porteur) { this.porteur = porteur; }
    public String getExpiration() { return expiration; }
    public void setExpiration(String expiration) { this.expiration = expiration; }
    public BigDecimal getPlafondJournalier() { return plafondJournalier; }
    public void setPlafondJournalier(BigDecimal plafondJournalier) { this.plafondJournalier = plafondJournalier; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public BigDecimal getPlafondRetrait() { return plafondRetrait; }
    public void setPlafondRetrait(BigDecimal plafondRetrait) { this.plafondRetrait = plafondRetrait; }
    public boolean isBloque() { return bloque; }
    public void setBloque(boolean bloque) { this.bloque = bloque; }
}
