package com.albaraka.backend.repository;

import com.albaraka.backend.model.CatalogExemplar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogExemplarRepository extends JpaRepository<CatalogExemplar, Long> {
    List<CatalogExemplar> findAllByOrderByDomainAscIntentAscIdAsc();
    List<CatalogExemplar> findByDomainOrderByIntentAscIdAsc(String domain);
    List<CatalogExemplar> findByActiveTrueOrderByDomainAscIntentAscIdAsc();
    boolean existsByDomainAndIntentAndLanguageAndText(String domain, String intent, String language, String text);
}
