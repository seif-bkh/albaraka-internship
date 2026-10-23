package com.albaraka.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "mot_de_passe", nullable = false)
    private String motDePasse;

    private String nom;
    private String prenom;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean actif = true;

    @Column(name = "date_creation")
    private LocalDateTime dateCreation;

    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;

    // ── Profil portail client (V8) ──────────────────────────────────────────
    @Column(name = "telephone")
    private String telephone;

    @Column(name = "adresse")
    private String adresse;

    @Column(name = "ville")
    private String ville;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "langue_preferee")
    private String languePreferee = "fr";

    /** PARTICULIER | PROFESSIONNEL | ENTREPRISE (null → non qualifié). */
    @Column(name = "segment")
    private String segment;

    /** Référence commerciale unique (ABK######), assignée à la première lecture du profil. */
    @Column(name = "reference_client")
    private String referenceClient;

    // ── Relation bancaire (V15) ─────────────────────────────────────────────
    /** Branch the customer belongs to; reference data seeded by V15. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agence_id")
    private Agence agence;

    /** EN_COURS | VALIDE | A_REVALIDER | REJETE. */
    @Column(name = "statut_kyc", nullable = false, length = 20)
    private String statutKyc = "VALIDE";

    /** FAIBLE | MOYEN | ELEVE. */
    @Column(name = "niveau_risque", nullable = false, length = 20)
    private String niveauRisque = "FAIBLE";

    /** National identity number — never leaves the bank, never enters a prompt. */
    @Column(name = "cin", length = 20)
    private String cin;

    @Column(name = "situation_professionnelle", length = 60)
    private String situationProfessionnelle;

    @Column(name = "employeur", length = 160)
    private String employeur;

    @Column(name = "revenu_mensuel", precision = 14, scale = 3)
    private BigDecimal revenuMensuel;

    /** Start of the banking relationship; drives "client depuis 2018" style answers. */
    @Column(name = "relation_depuis")
    private LocalDate relationDepuis;

    @PrePersist
    protected void onCreate() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    
    public LocalDateTime getDerniereConnexion() { return derniereConnexion; }
    public void setDerniereConnexion(LocalDateTime derniereConnexion) { this.derniereConnexion = derniereConnexion; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }
    public String getLanguePreferee() { return languePreferee; }
    public void setLanguePreferee(String languePreferee) { this.languePreferee = languePreferee; }
    public String getSegment() { return segment; }
    public void setSegment(String segment) { this.segment = segment; }
    public String getReferenceClient() { return referenceClient; }
    public void setReferenceClient(String referenceClient) { this.referenceClient = referenceClient; }

    public Agence getAgence() { return agence; }
    public void setAgence(Agence agence) { this.agence = agence; }
    public String getStatutKyc() { return statutKyc; }
    public void setStatutKyc(String statutKyc) { this.statutKyc = statutKyc; }
    public String getNiveauRisque() { return niveauRisque; }
    public void setNiveauRisque(String niveauRisque) { this.niveauRisque = niveauRisque; }
    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }
    public String getSituationProfessionnelle() { return situationProfessionnelle; }
    public void setSituationProfessionnelle(String v) { this.situationProfessionnelle = v; }
    public String getEmployeur() { return employeur; }
    public void setEmployeur(String employeur) { this.employeur = employeur; }
    public BigDecimal getRevenuMensuel() { return revenuMensuel; }
    public void setRevenuMensuel(BigDecimal revenuMensuel) { this.revenuMensuel = revenuMensuel; }
    public LocalDate getRelationDepuis() { return relationDepuis; }
    public void setRelationDepuis(LocalDate relationDepuis) { this.relationDepuis = relationDepuis; }
}
