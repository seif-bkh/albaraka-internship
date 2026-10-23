package com.albaraka.backend.repository;

import com.albaraka.backend.model.Financement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FinancementRepository extends JpaRepository<Financement, Long> {
    List<Financement> findByClientIdOrderByDateDebutDesc(Long clientId);
    long countByClientIdAndStatutIn(Long clientId, List<String> statuts);
}
