package com.albaraka.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One account movement (portal demo data — see V8__client_portal). */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_id", nullable = false)
    private Compte compte;

    @Column(name = "date_operation", nullable = false)
    private LocalDateTime dateOperation;

    @Column(nullable = false)
    private String libelle;

    @Column
    private String categorie;

    /** Positive = credit, negative = debit. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal montant;

    @Column(nullable = false)
    private String devise = "TND";

    /** Value date: when the funds actually move, as opposed to the booking date. */
    @Column(name = "date_valeur")
    private LocalDateTime dateValeur;

    @Column(length = 40)
    private String reference;

    /** AGENCE | DAB | CARTE | VIREMENT | PRELEVEMENT | EN_LIGNE. */
    @Column(length = 24)
    private String canal;

    @Column(length = 160)
    private String contrepartie;

    /** EN_ATTENTE | COMPTABILISEE | REJETEE. */
    @Column(nullable = false, length = 20)
    private String statut = "COMPTABILISEE";

    @PrePersist
    protected void onCreate() {
        if (dateOperation == null) {
            dateOperation = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Compte getCompte() { return compte; }
    public void setCompte(Compte compte) { this.compte = compte; }
    public LocalDateTime getDateOperation() { return dateOperation; }
    public void setDateOperation(LocalDateTime dateOperation) { this.dateOperation = dateOperation; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public LocalDateTime getDateValeur() { return dateValeur; }
    public void setDateValeur(LocalDateTime dateValeur) { this.dateValeur = dateValeur; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getContrepartie() { return contrepartie; }
    public void setContrepartie(String contrepartie) { this.contrepartie = contrepartie; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
}
