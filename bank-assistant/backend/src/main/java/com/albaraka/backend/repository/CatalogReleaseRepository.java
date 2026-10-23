package com.albaraka.backend.repository;

import com.albaraka.backend.model.CatalogRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogReleaseRepository extends JpaRepository<CatalogRelease, Long> {
    Optional<CatalogRelease> findByVersion(String version);
    Optional<CatalogRelease> findTopByOrderBySealedAtDescIdDesc();
    List<CatalogRelease> findAllByOrderBySealedAtDesc();
}
