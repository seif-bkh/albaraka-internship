package com.albaraka.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomFichier;
    private String cheminStockage;
    private String type;

    private LocalDateTime dateDepot;
    private LocalDateTime dateMiseAJour;

    private String version;

    @Enumerated(EnumType.STRING)
    private StatutDocument statut;

    private String langue;
    private String produit;
    private Long tailleFichier;

    @ManyToOne
    @JoinColumn(name = "depose_par_id")
    private User deposePar;

    @Enumerated(EnumType.STRING)
    private StatutIndexation statutIndexation;

    @Column(columnDefinition = "text")
    private String messageErreurIndexation;

    @PrePersist
    protected void onCreate() {
        dateDepot = LocalDateTime.now();
        dateMiseAJour = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        dateMiseAJour = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNomFichier() { return nomFichier; }
    public void setNomFichier(String nomFichier) { this.nomFichier = nomFichier; }
    public String getCheminStockage() { return cheminStockage; }
    public void setCheminStockage(String cheminStockage) { this.cheminStockage = cheminStockage; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getDateDepot() { return dateDepot; }
    public void setDateDepot(LocalDateTime dateDepot) { this.dateDepot = dateDepot; }
    public LocalDateTime getDateMiseAJour() { return dateMiseAJour; }
    public void setDateMiseAJour(LocalDateTime dateMiseAJour) { this.dateMiseAJour = dateMiseAJour; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public StatutDocument getStatut() { return statut; }
    public void setStatut(StatutDocument statut) { this.statut = statut; }
    public String getLangue() { return langue; }
    public void setLangue(String langue) { this.langue = langue; }
    public String getProduit() { return produit; }
    public void setProduit(String produit) { this.produit = produit; }
    public Long getTailleFichier() { return tailleFichier; }
    public void setTailleFichier(Long tailleFichier) { this.tailleFichier = tailleFichier; }
    public User getDeposePar() { return deposePar; }
    public void setDeposePar(User deposePar) { this.deposePar = deposePar; }
    public StatutIndexation getStatutIndexation() { return statutIndexation; }
    public void setStatutIndexation(StatutIndexation statutIndexation) { this.statutIndexation = statutIndexation; }
    public String getMessageErreurIndexation() { return messageErreurIndexation; }
    public void setMessageErreurIndexation(String messageErreurIndexation) { this.messageErreurIndexation = messageErreurIndexation; }
}
