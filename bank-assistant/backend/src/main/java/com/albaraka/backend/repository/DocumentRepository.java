package com.albaraka.backend.repository;

import com.albaraka.backend.model.Document;
import com.albaraka.backend.model.StatutDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    Page<Document> findByStatut(StatutDocument statut, Pageable pageable);

    // The ingestion worker reports results keyed by the physical filename it was
    // handed: the opaque stored name (cheminStockage) for hardened uploads, or
    // the legacy original name (nomFichier) for rows predating upload hardening.
    Optional<Document> findFirstByCheminStockage(String cheminStockage);

    Optional<Document> findFirstByNomFichier(String nomFichier);
}
