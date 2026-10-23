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

import java.time.LocalDateTime;

/**
 * A standing beneficiary ("mes bénéficiaires") — a saved counterparty for transfers.
 *
 * <p>The RIB is stored as the customer recorded it; the assistant never quotes it back
 * (only the count of saved beneficiaries reaches a prompt, see CustomerContextService).
 */
@Entity
@Table(name = "beneficiaires")
public class Beneficiaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false, length = 160)
    private String nom;

    @Column(length = 40)
    private String rib;

    @Column(length = 160)
    private String banque;

    @Column(nullable = false, length = 80)
    private String pays = "TN";

    /** VIREMENT | PRELEVEMENT | INTERNE. */
    @Column(nullable = false, length = 20)
    private String type = "VIREMENT";

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "cree_le", nullable = false)
    private LocalDateTime creeLe;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getRib() { return rib; }
    public void setRib(String rib) { this.rib = rib; }
    public String getBanque() { return banque; }
    public void setBanque(String banque) { this.banque = banque; }
    public String getPays() { return pays; }
    public void setPays(String pays) { this.pays = pays; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public LocalDateTime getCreeLe() { return creeLe; }
    public void setCreeLe(LocalDateTime creeLe) { this.creeLe = creeLe; }
}
