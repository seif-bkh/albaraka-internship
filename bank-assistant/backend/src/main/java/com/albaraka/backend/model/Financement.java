package com.albaraka.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One financing contract (Mourabaha, Ijara, Moucharaka, Moudaraba, Salam, Istisna).
 *
 * <p>Read-only from this application's point of view: the core banking system books
 * the contract, the portal and the assistant only read it. Amounts are the contract's
 * own figures (financed amount, monthly instalment, outstanding principal), never a
 * recomputation done here.
 */
@Entity
@Table(name = "financements")
public class Financement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    /** MOURABAHA | IJARA | MOUCHARAKA | MOUDARABA | SALAM | ISTISNA. */
    @Column(nullable = false, length = 24)
    private String produit;

    @Column(length = 200)
    private String objet;

    @Column(name = "montant_finance", nullable = false, precision = 14, scale = 3)
    private BigDecimal montantFinance;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal apport = BigDecimal.ZERO;

    /** Profit margin for the contract (percentage, not an interest rate). */
    @Column(name = "marge_taux", precision = 6, scale = 3)
    private BigDecimal margeTaux;

    @Column(name = "duree_mois", nullable = false)
    private Integer dureeMois;

    @Column(name = "echeance_mensuelle", precision = 14, scale = 3)
    private BigDecimal echeanceMensuelle;

    @Column(name = "capital_restant", precision = 14, scale = 3)
    private BigDecimal capitalRestant;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "prochaine_echeance")
    private LocalDate prochaineEcheance;

    /** DEMANDE | EN_COURS | EN_RETARD | CLOTURE | REJETE. */
    @Column(nullable = false, length = 20)
    private String statut = "EN_COURS";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getProduit() { return produit; }
    public void setProduit(String produit) { this.produit = produit; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public BigDecimal getMontantFinance() { return montantFinance; }
    public void setMontantFinance(BigDecimal montantFinance) { this.montantFinance = montantFinance; }
    public BigDecimal getApport() { return apport; }
    public void setApport(BigDecimal apport) { this.apport = apport; }
    public BigDecimal getMargeTaux() { return margeTaux; }
    public void setMargeTaux(BigDecimal margeTaux) { this.margeTaux = margeTaux; }
    public Integer getDureeMois() { return dureeMois; }
    public void setDureeMois(Integer dureeMois) { this.dureeMois = dureeMois; }
    public BigDecimal getEcheanceMensuelle() { return echeanceMensuelle; }
    public void setEcheanceMensuelle(BigDecimal echeanceMensuelle) { this.echeanceMensuelle = echeanceMensuelle; }
    public BigDecimal getCapitalRestant() { return capitalRestant; }
    public void setCapitalRestant(BigDecimal capitalRestant) { this.capitalRestant = capitalRestant; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public LocalDate getProchaineEcheance() { return prochaineEcheance; }
    public void setProchaineEcheance(LocalDate prochaineEcheance) { this.prochaineEcheance = prochaineEcheance; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Agence getAgence() { return agence; }
    public void setAgence(Agence agence) { this.agence = agence; }
}
