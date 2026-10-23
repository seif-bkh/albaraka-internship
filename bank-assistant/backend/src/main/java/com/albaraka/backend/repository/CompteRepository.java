package com.albaraka.backend.repository;

import com.albaraka.backend.model.Compte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompteRepository extends JpaRepository<Compte, Long> {
    List<Compte> findByClientIdOrderByIdAsc(Long clientId);
}
