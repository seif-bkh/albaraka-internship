package com.albaraka.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A bank account of a {@link Client} (portal demo data — see V8__client_portal).
 * Read-only from the portal's point of view: the backend never writes balances.
 */
@Entity
@Table(name = "comptes")
public class Compte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String libelle;

    /** COMPTE_COURANT | EPARGNE | DEPOT. */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, unique = true)
    private String iban;

    @Column(nullable = false)
    private String devise = "TND";

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal solde = BigDecimal.ZERO;

    @Column(name = "date_ouverture", nullable = false)
    private LocalDateTime dateOuverture;

    /** ACTIF | BLOQUE | CLOTURE. */
    @Column(nullable = false, length = 20)
    private String statut = "ACTIF";

    /** Available balance as the core banking system reports it (null → same as solde). */
    @Column(name = "solde_disponible", precision = 14, scale = 3)
    private BigDecimal soldeDisponible;

    @Column(name = "decouvert_autorise", nullable = false, precision = 14, scale = 3)
    private BigDecimal decouvertAutorise = BigDecimal.ZERO;

    @PrePersist
    protected void onCreate() {
        if (dateOuverture == null) {
            dateOuverture = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public BigDecimal getSoldeDisponible() { return soldeDisponible; }
    public void setSoldeDisponible(BigDecimal soldeDisponible) { this.soldeDisponible = soldeDisponible; }
    public BigDecimal getDecouvertAutorise() { return decouvertAutorise; }
    public void setDecouvertAutorise(BigDecimal decouvertAutorise) { this.decouvertAutorise = decouvertAutorise; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public BigDecimal getSolde() { return solde; }
    public void setSolde(BigDecimal solde) { this.solde = solde; }
    public LocalDateTime getDateOuverture() { return dateOuverture; }
    public void setDateOuverture(LocalDateTime dateOuverture) { this.dateOuverture = dateOuverture; }
}
