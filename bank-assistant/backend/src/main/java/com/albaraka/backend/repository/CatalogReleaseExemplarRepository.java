package com.albaraka.backend.repository;

import com.albaraka.backend.model.CatalogReleaseExemplar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogReleaseExemplarRepository extends JpaRepository<CatalogReleaseExemplar, Long> {
    List<CatalogReleaseExemplar> findByReleaseIdOrderByDomainAscIntentAscIdAsc(Long releaseId);
}
