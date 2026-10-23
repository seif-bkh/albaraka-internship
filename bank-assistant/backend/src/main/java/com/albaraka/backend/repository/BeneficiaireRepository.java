package com.albaraka.backend.repository;

import com.albaraka.backend.model.Beneficiaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BeneficiaireRepository extends JpaRepository<Beneficiaire, Long> {
    List<Beneficiaire> findByClientIdOrderByNomAsc(Long clientId);
    long countByClientIdAndActifTrue(Long clientId);
}
