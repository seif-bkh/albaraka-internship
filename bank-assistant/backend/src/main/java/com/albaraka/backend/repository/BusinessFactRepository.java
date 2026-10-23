package com.albaraka.backend.repository;

import com.albaraka.backend.model.BusinessFact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessFactRepository extends JpaRepository<BusinessFact, Long> {
    List<BusinessFact> findAllByOrderByDomainAscKeyAsc();
    List<BusinessFact> findByDomainOrderByKeyAsc(String domain);
    Optional<BusinessFact> findByDomainAndKey(String domain, String key);
    /** Active facts of one domain/type — e.g. simulator rates, contact numbers. */
    List<BusinessFact> findByDomainAndFactTypeAndActiveTrueOrderByKeyAsc(String domain, String factType);
}
